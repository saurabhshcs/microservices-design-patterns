package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.saga;
public interface SagaStep<T> {
    SagaResult execute(T context);
    SagaResult compensate(T context);
    String stepName();
}
