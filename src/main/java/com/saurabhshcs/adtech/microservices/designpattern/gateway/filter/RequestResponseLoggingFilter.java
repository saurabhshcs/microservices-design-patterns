package com.saurabhshcs.adtech.microservices.designpattern.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that logs request and response metadata for observability.
 * <p>
 * Logs include the correlation ID, HTTP method, path, response status,
 * and elapsed time. These structured logs integrate with ELK, Datadog,
 * or any log aggregation platform.
 * </p>
 */
@Component
@Slf4j
public class RequestResponseLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();

        String correlationId = request.getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getURI().getPath();

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatusCode() != null
                    ? response.getStatusCode().value()
                    : 0;

            log.info("[{}] {} {} -> {} ({}ms)",
                    correlationId, method, path, status, duration);
        }));
    }

    @Override
    public int getOrder() {
        return -150;  // After correlation ID, before JWT
    }
}
