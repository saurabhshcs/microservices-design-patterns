package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;
@Data @Builder
public class Order {
    private UUID orderId;
    private String customerId;
    private String productId;
    private int quantity;
    private BigDecimal amount;
    private OrderStatus status;
    private String failureReason;
    public static Order create(String customerId, String productId, int quantity, BigDecimal amount) {
        return Order.builder().orderId(UUID.randomUUID()).customerId(customerId)
                .productId(productId).quantity(quantity).amount(amount)
                .status(OrderStatus.PENDING).build();
    }
    public void updateStatus(OrderStatus newStatus) { this.status = newStatus; }
    public void fail(String reason) { this.status = OrderStatus.FAILED; this.failureReason = reason; }
}
