package com.saurabhshcs.adtech.microservices.designpattern.circuitbreaker.payment.client;

import com.saurabhshcs.adtech.microservices.designpattern.circuitbreaker.payment.model.PaymentRequest;
import com.saurabhshcs.adtech.microservices.designpattern.circuitbreaker.payment.model.PaymentResponse;

/**
 * Abstraction over the external payment gateway.
 * In production this would be an HTTP client (WebClient/RestTemplate).
 * The interface makes it easy to inject faults in tests.
 */
public interface PaymentGatewayClient {
    PaymentResponse charge(PaymentRequest request);
}
