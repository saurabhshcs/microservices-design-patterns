package com.saurabhshcs.adtech.microservices.designpattern.circuitbreaker.payment.model;

import java.util.UUID;

public record PaymentResponse(UUID paymentId, String status, String message) {
    public static PaymentResponse success(UUID id) {
        return new PaymentResponse(id, "SUCCESS", "Payment processed");
    }
    public static PaymentResponse failed(UUID id, String reason) {
        return new PaymentResponse(id, "FAILED", reason);
    }
    public static PaymentResponse fallback(UUID id) {
        return new PaymentResponse(id, "PENDING", "Payment gateway unavailable — queued for retry");
    }
}
