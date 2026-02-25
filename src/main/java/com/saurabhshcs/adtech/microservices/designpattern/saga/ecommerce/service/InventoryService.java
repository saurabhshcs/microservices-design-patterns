package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.service;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.Order;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.OrderStatus;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.saga.SagaResult;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.saga.SagaStep;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class InventoryService implements SagaStep<Order> {
    private final ConcurrentHashMap<String, Integer> stock = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> reservations = new ConcurrentHashMap<>();
    public InventoryService() {
        stock.put("PROD-001", 100); stock.put("PROD-002", 25);
        stock.put("PROD-003", 0);   stock.put("PROD-004", 5);
    }
    @Override
    public SagaResult execute(Order order) {
        int available = stock.getOrDefault(order.getProductId(), 0);
        if (available < order.getQuantity())
            return SagaResult.failure(stepName(), "Insufficient stock for " + order.getProductId() + ". Available: " + available);
        stock.merge(order.getProductId(), -order.getQuantity(), Integer::sum);
        reservations.put(order.getOrderId(), order.getQuantity());
        order.updateStatus(OrderStatus.INVENTORY_RESERVED);
        return SagaResult.success(stepName());
    }
    @Override
    public SagaResult compensate(Order order) {
        Integer reserved = reservations.remove(order.getOrderId());
        if (reserved != null) {
            stock.merge(order.getProductId(), reserved, Integer::sum);
            order.updateStatus(OrderStatus.INVENTORY_FAILED);
        }
        return SagaResult.success(stepName() + "-compensation");
    }
    @Override public String stepName() { return "InventoryService"; }
    public int getAvailableStock(String productId) { return stock.getOrDefault(productId, 0); }
}
