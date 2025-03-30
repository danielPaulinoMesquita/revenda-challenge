package br.com.daniel.testerevenda.service.impl;

import br.com.daniel.testerevenda.client.ResaleCompanyFeignClient;
import br.com.daniel.testerevenda.dto.CustomerOrderSummary;
import br.com.daniel.testerevenda.dto.Produto;
import br.com.daniel.testerevenda.dto.request.OrderRequest;
import br.com.daniel.testerevenda.dto.request.ResaleRequest;
import br.com.daniel.testerevenda.dto.response.CustomerResponse;
import br.com.daniel.testerevenda.dto.response.OrderResponse;
import br.com.daniel.testerevenda.dto.response.ResaleResponse;
import br.com.daniel.testerevenda.mapper.OrderMapper;
import br.com.daniel.testerevenda.mapper.ProdutoMapper;
import br.com.daniel.testerevenda.model.Order;
import br.com.daniel.testerevenda.model.ProdutoModel;
import br.com.daniel.testerevenda.repository.OrderRepository;
import br.com.daniel.testerevenda.service.CustomerService;
import br.com.daniel.testerevenda.service.OrderService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger logger = LogManager.getLogger(OrderServiceImpl.class);

    private static final int attempts = 2;
    private static final int minDelay = 3000;

    private final int minQuantity;
    private final OrderRepository orderRepository;
    private final CustomerService customerService;
    private final OrderMapper orderMapper;
    private final ProdutoMapper produtoMapper;
    private final ResaleCompanyFeignClient resaleCompanyFeignClient;

    public OrderServiceImpl(
            @Value("${business.rules.minimum}") int minQuantity,
            OrderRepository orderRepository,
            CustomerService customerService,
            OrderMapper orderMapper,
            ProdutoMapper produtoMapper,
            ResaleCompanyFeignClient resaleCompanyFeignClient) {
        this.minQuantity = minQuantity;
        this.orderRepository = orderRepository;
        this.customerService = customerService;
        this.orderMapper = orderMapper;
        this.produtoMapper = produtoMapper;
        this.resaleCompanyFeignClient = resaleCompanyFeignClient;
    }

    @Override
    public OrderResponse save(OrderRequest orderRequest) {
        validateCustomer(orderRequest.customerId());
        return orderMapper.toOrderResponse(orderRepository.save(orderMapper.toOrder(orderRequest)));
    }

    @Override
    public CustomerResponse validateCustomer(final String customerId) {
        return customerService.findById(customerId);
    }

    private Order findByCustomerId(final String customerId) {
        return orderRepository.findOrdersByCustomerId(customerId);
    }

    @Override
    @Scheduled(cron = "0 * * * * *")
    public void schedulingResaleOrder() {
        logger.info("início do agendamento de revenda");

        try {
            List<Order> orders = getOrdersWithHighQuantity();

            if (orders.isEmpty()) {
                logger.warn("nenhum pedido apto para revenda encontrado");
                return;
            }

            processResaleOrder(orders);
        } catch (Exception e) {
            logger.error("erro na verificação de pedidos aptos para revendas: {}", e.getMessage());
        }
    }

    private List<Order> getOrdersWithHighQuantity() {
        logger.info("buscando pedidos aptos para revenda");

        List<CustomerOrderSummary> customerSummaries = orderRepository
                .findOrdersByCustomerIdsWithTotalQuantityGreaterThan(minQuantity);

        if (customerSummaries.isEmpty()) {
            logger.warn("nenhum resumo de pedido encontrado para a quantidade mínima especificada.");
            return Collections.emptyList();
        }

        List<String> customerIds = customerSummaries.stream()
                .map(CustomerOrderSummary::getCustomerId)
                .collect(Collectors.toList());

        List<Order> orders = orderRepository.findByCustomerIdIn(customerIds);

        return getOrdersWithJoinProducts(orders);

    }

    private List<Order> getOrdersWithJoinProducts(List<Order> orders) {
        logger.info("somando produtos por cliente");

        Map<String, Set<ProdutoModel>> productMap = new HashMap<>();

        for (Order order : orders) {
            productMap.computeIfAbsent(order.getCustomerId(), k -> new HashSet<>());
            productMap.get(order.getCustomerId()).addAll(order.getProdutos());
        }

        return orders.stream()
                .filter(distinctByKey(Order::getCustomerId))
                .map((order) -> {
                    order.setProdutos(productMap.get(order.getCustomerId()));
                    return order;
                }).toList();
    }

    private void processResaleOrder(List<Order> orders) {
        logger.info("início do processo revenda de pedidos");
        List<ResaleRequest> resaleRequests =  orders.stream()
                .map(order -> {
                    Set<Produto> produtos = order.getProdutos()
                            .stream()
                            .map(produtoMapper::toProduto)
                            .collect(Collectors.toSet());
                    return new ResaleRequest(order.getCustomerId(), produtos);
                }).toList();

        for (ResaleRequest resaleRequest : resaleRequests) {
            try {
                ResponseEntity<ResaleResponse> res = resaleOrder(resaleRequest);

                if (Objects.nonNull(res) &&
                        res.getStatusCode().is2xxSuccessful()) {
                    Order order = findByCustomerId(resaleRequest.customerId());

                    order.setOrderResaleId(res.getBody().orderId());
                    order.setResaleIsDone(true);
                    orderRepository.save(order);
                }
            } catch (RuntimeException e) {
                logger.error("erro ao realizar ação de revenda: {}", e.getMessage());
            }
        }
        logger.info("finalizado o processo revenda de pedidos");
    }

    @Retryable(
            maxAttempts = attempts,
            backoff = @Backoff(delay = minDelay))
    public ResponseEntity<ResaleResponse> resaleOrder(ResaleRequest resaleRequest){
        return resaleCompanyFeignClient.getResaleOrder(resaleRequest);
    }

    private static <T> java.util.function.Predicate<T> distinctByKey(java.util.function.Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new HashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }
}
