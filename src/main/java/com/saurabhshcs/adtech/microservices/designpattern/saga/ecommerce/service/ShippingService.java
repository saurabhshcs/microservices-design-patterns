package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.service;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.Order;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.OrderStatus;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.saga.SagaResult;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.saga.SagaStep;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class ShippingService implements SagaStep<Order> {
    private final ConcurrentHashMap<UUID, String> shipments = new ConcurrentHashMap<>();
    @Override
    public SagaResult execute(Order order) {
        String tracking = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        shipments.put(order.getOrderId(), tracking);
        order.updateStatus(OrderStatus.SHIPPING_SCHEDULED);
        return SagaResult.success(stepName());
    }
    @Override
    public SagaResult compensate(Order order) {
        String tracking = shipments.remove(order.getOrderId());
        if (tracking != null) order.updateStatus(OrderStatus.SHIPPING_FAILED);
        return SagaResult.success(stepName() + "-compensation");
    }
    @Override public String stepName() { return "ShippingService"; }
}
