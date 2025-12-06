package com.saurabhshcs.adtech.microservices.designpattern.saga.common;

public final class TestLogConstants {

    private TestLogConstants() {
        // Utility class - prevent instantiation
    }

    public static final String ORCHESTRATION_FAILED_TEXT = "Campaign orchestration failed";
    public static final String COMPENSATION_TEXT = "Compensating transaction";
}
