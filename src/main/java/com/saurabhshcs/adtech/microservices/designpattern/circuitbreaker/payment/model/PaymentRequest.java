package com.saurabhshcs.adtech.microservices.designpattern.circuitbreaker.payment.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(UUID paymentId, String customerId, BigDecimal amount, String currency) {
    public static PaymentRequest of(String customerId, BigDecimal amount, String currency) {
        return new PaymentRequest(UUID.randomUUID(), customerId, amount, currency);
    }
}
