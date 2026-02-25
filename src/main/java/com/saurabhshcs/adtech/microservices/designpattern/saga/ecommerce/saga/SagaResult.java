package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.saga;
import lombok.Builder;
import lombok.Getter;
@Getter @Builder
public class SagaResult {
    private final boolean success;
    private final String stepName;
    private final String message;
    public static SagaResult success(String stepName) {
        return SagaResult.builder().success(true).stepName(stepName)
                .message("Step '" + stepName + "' completed successfully").build();
    }
    public static SagaResult failure(String stepName, String reason) {
        return SagaResult.builder().success(false).stepName(stepName)
                .message("Step '" + stepName + "' failed: " + reason).build();
    }
}
