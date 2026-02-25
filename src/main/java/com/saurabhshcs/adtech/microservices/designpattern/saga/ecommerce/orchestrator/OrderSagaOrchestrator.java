package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.orchestrator;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.Order;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.OrderStatus;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.saga.SagaResult;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.saga.SagaStep;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.service.InventoryService;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.service.PaymentService;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
@Slf4j @Service @RequiredArgsConstructor
public class OrderSagaOrchestrator {
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final ShippingService shippingService;
    public Order processOrder(Order order) {
        List<SagaStep<Order>> executedSteps = new ArrayList<>();
        List<SagaStep<Order>> steps = List.of(paymentService, inventoryService, shippingService);
        for (SagaStep<Order> step : steps) {
            log.info("Executing saga step: {} for order: {}", step.stepName(), order.getOrderId());
            SagaResult result = step.execute(order);
            if (result.isSuccess()) {
                executedSteps.add(0, step);
            } else {
                log.warn("Saga step failed: {}. Starting compensation.", result.getMessage());
                order.fail(result.getMessage());
                performCompensation(order, executedSteps);
                return order;
            }
        }
        order.updateStatus(OrderStatus.COMPLETED);
        log.info("Saga completed successfully for order: {}", order.getOrderId());
        return order;
    }
    private void performCompensation(Order order, List<SagaStep<Order>> executedSteps) {
        order.updateStatus(OrderStatus.COMPENSATING);
        for (SagaStep<Order> step : executedSteps) {
            log.info("Compensating step: {} for order: {}", step.stepName(), order.getOrderId());
            step.compensate(order);
        }
        order.updateStatus(OrderStatus.COMPENSATION_COMPLETED);
    }
}
