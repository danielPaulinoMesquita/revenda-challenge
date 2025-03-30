package br.com.daniel.testerevenda.service;

import br.com.daniel.testerevenda.client.ResaleCompanyFeignClient;
import br.com.daniel.testerevenda.dto.response.OrderResponse;
import br.com.daniel.testerevenda.dto.response.ResaleResponse;
import br.com.daniel.testerevenda.mapper.OrderMapper;
import br.com.daniel.testerevenda.mapper.ProdutoMapper;
import br.com.daniel.testerevenda.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Arrays;
import java.util.List;

import static br.com.daniel.testerevenda.utils.FactoryUtils.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    private int minQuantity = 5;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private OrderMapper orderMapper;

    @MockitoBean
    private ProdutoMapper produtoMapper;

    @MockitoBean
    private ResaleCompanyFeignClient resaleCompanyFeignClient;

    @Test
    void testSaveOrderWithSuccess() {
        final var customerId = "123";
        final var reservationId = getOrderRequest("123");

        when(orderMapper.toOrder(getOrderRequest(customerId))).thenReturn(getOrder());
        when(orderMapper.toOrderResponse(getOrder())).thenReturn(getOrderResponse(customerId));
        when(orderRepository.save(getOrder())).thenReturn(getOrder());

        OrderResponse order = orderService.save(reservationId);

        assertNotNull(order);
        verify(orderRepository).save(getOrder());
        verify(customerService).findById(customerId);
    }

    @Test
    void testProcessingSchedulingResale() {
        when(orderRepository.findOrdersByCustomerIdsWithTotalQuantityGreaterThan(anyInt()))
                .thenReturn(List.of(getCustomerOrderSummary(), getCustomerOrderSummary()));

        when(orderRepository.findByCustomerIdIn(any())).thenReturn(List.of(getOrder(), getOrder()));

        orderService.schedulingResaleOrder();

        verify(orderRepository, atLeastOnce()).findOrdersByCustomerIdsWithTotalQuantityGreaterThan(anyInt());
        verify(orderRepository, atLeastOnce()).findByCustomerIdIn(any());
    }

    @Test
    void testProcessingSchedulingResaleWithProductsEnough() {
        when(orderRepository.findOrdersByCustomerIdsWithTotalQuantityGreaterThan(anyInt()))
                .thenReturn(List.of(getCustomerOrderSummary(), getCustomerOrderSummary()));

        when(orderRepository.findByCustomerIdIn(any())).thenReturn(List.of(getOrder(), getOrder()));

        orderService.schedulingResaleOrder();

        verify(orderRepository, atLeastOnce()).findOrdersByCustomerIdsWithTotalQuantityGreaterThan(anyInt());
        verify(orderRepository, atLeastOnce()).findByCustomerIdIn(any());
    }

    @Test
    void testSchedulingResaleCallExternalApi() {
        when(orderRepository.findOrdersByCustomerIdsWithTotalQuantityGreaterThan(anyInt()))
                .thenReturn(List.of(getCustomerOrderSummary(), getCustomerOrderSummary()));
        when(orderRepository.findByCustomerIdIn(any())).thenReturn(List.of(getOrder(), getOrder()));

        ResponseEntity<ResaleResponse> mockResponseEntity = ResponseEntity.ok(getResaleResponse());
        when(resaleCompanyFeignClient.getResaleOrder(any())).thenReturn(mockResponseEntity);
        when(orderRepository.findOrdersByCustomerId(any())).thenReturn(getOrder());
        when(orderRepository.save(getOrderResaleDone())).thenReturn(getOrderResaleDone());

        orderService.schedulingResaleOrder();

        verify(orderRepository, atLeastOnce()).findOrdersByCustomerIdsWithTotalQuantityGreaterThan(anyInt());
        verify(orderRepository, atLeastOnce()).findByCustomerIdIn(any());
    }

    @Test
    void testSchedulingResaleWithNoResponse() {
        ResponseEntity<ResaleResponse> mockResponseEntity = ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(orderRepository.findOrdersByCustomerIdsWithTotalQuantityGreaterThan(anyInt()))
                .thenReturn(List.of(getCustomerOrderSummary(), getCustomerOrderSummary()));
        when(orderRepository.findByCustomerIdIn(any())).thenReturn(List.of(getOrder(), getOrder()));
        when(resaleCompanyFeignClient.getResaleOrder(any())).thenReturn(mockResponseEntity);
        when(orderRepository.findOrdersByCustomerId(any())).thenReturn(getOrder());

        orderService.schedulingResaleOrder();

        verify(orderRepository, never()).save(any());
    }

    @Test
    void testSchedulingResaleThrowException() {
        when(orderRepository.findOrdersByCustomerIdsWithTotalQuantityGreaterThan(anyInt()))
                .thenReturn(List.of(getCustomerOrderSummary(), getCustomerOrderSummary()));
        when(orderRepository.findByCustomerIdIn(any())).thenReturn(List.of(getOrder(), getOrder()));
        when(resaleCompanyFeignClient.getResaleOrder(any())).thenThrow(new RuntimeException());
        when(orderRepository.findOrdersByCustomerId(any())).thenReturn(getOrder());

        orderService.schedulingResaleOrder();

        verify(orderRepository, never()).save(any());
    }

    @Test
    void testSchedulingResaleNoProductsEnough() {
        when(orderRepository.findOrdersByCustomerIdsWithTotalQuantityGreaterThan(anyInt()))
                .thenReturn(Arrays.asList());

        orderService.schedulingResaleOrder();

        verify(orderRepository, never()).findByCustomerIdIn(any());
        verify(resaleCompanyFeignClient, never()).getResaleOrder(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void testSchedulingResaleNullPointer() {
        when(orderRepository.findOrdersByCustomerIdsWithTotalQuantityGreaterThan(anyInt()))
                .thenThrow(NullPointerException.class);

        orderService.schedulingResaleOrder();

        verify(orderRepository, never()).findByCustomerIdIn(any());
        verify(resaleCompanyFeignClient, never()).getResaleOrder(any());
        verify(orderRepository, never()).save(any());
    }

}
