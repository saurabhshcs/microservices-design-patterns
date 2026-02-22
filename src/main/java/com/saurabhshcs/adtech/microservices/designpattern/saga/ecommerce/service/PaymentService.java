package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.service;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.Order;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.OrderStatus;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.saga.SagaResult;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.saga.SagaStep;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class PaymentService implements SagaStep<Order> {
    private final ConcurrentHashMap<UUID, String> processedPayments = new ConcurrentHashMap<>();
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000");
    @Override
    public SagaResult execute(Order order) {
        if (order.getAmount().compareTo(MAX_AMOUNT) > 0)
            return SagaResult.failure(stepName(), "Amount exceeds limit of " + MAX_AMOUNT);
        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        processedPayments.put(order.getOrderId(), paymentId);
        order.updateStatus(OrderStatus.PAYMENT_COMPLETED);
        return SagaResult.success(stepName());
    }
    @Override
    public SagaResult compensate(Order order) {
        String paymentId = processedPayments.remove(order.getOrderId());
        if (paymentId != null) order.updateStatus(OrderStatus.PAYMENT_FAILED);
        return SagaResult.success(stepName() + "-compensation");
    }
    @Override public String stepName() { return "PaymentService"; }
}
