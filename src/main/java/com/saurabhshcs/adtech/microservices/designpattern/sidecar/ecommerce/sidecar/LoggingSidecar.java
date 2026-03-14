package com.saurabhshcs.adtech.microservices.designpattern.sidecar.ecommerce.sidecar;

import lombok.extern.slf4j.Slf4j;

/**
 * Sidecar: structured request/response logging.
 * Completely decoupled from business logic.
 */
@Slf4j
public class LoggingSidecar {

    public void logRequest(String operation, Object request) {
        log.info("[SIDECAR] >>> {} | request={}", operation, request);
    }

    public void logResponse(String operation, Object response, long latencyMs) {
        log.info("[SIDECAR] <<< {} | response={} | latency={}ms", operation, response, latencyMs);
    }

    public void logError(String operation, Throwable error) {
        log.error("[SIDECAR] !!! {} | error={}", operation, error.getMessage(), error);
    }
}
