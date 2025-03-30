package br.com.daniel.testerevenda.repository;

import br.com.daniel.testerevenda.dto.CustomerOrderSummary;
import br.com.daniel.testerevenda.model.Order;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

    @Aggregation(pipeline = {
            "{ $unwind: '$produtos' }", // Desdobra o array de produtos
            "{ $group: { _id: '$customerId', totalQuantity: { $sum: '$produtos.quantidade' } } }", // Soma as quantidades
            "{ $match: { totalQuantity: { $gte: ?0 } } }" // Filtra pela quantidade total, incluindo 100
    })
    List<CustomerOrderSummary> findOrdersByCustomerIdsWithTotalQuantityGreaterThan(int minQuantity);

    //List<CustomerOrderSummary> findCustomerIdsWithTotalQuantityGreaterThan(int minQuantity);

    List<Order> findByCustomerIdIn(List<String> customerIds);

    Order findOrdersByCustomerId(String customerId);

    void deleteByCustomerId(String customerId);
}
