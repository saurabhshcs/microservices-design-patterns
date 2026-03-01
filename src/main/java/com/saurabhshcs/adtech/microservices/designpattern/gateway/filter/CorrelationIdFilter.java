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

import java.util.UUID;

/**
 * Global filter that ensures every request has a unique correlation ID.
 * <p>
 * If the incoming request already has an {@code X-Correlation-ID} header
 * (e.g., from an upstream load balancer), it is preserved. Otherwise, a
 * new UUID is generated.
 * </p>
 * <p>
 * The correlation ID is:
 * <ul>
 *   <li>Added to the request headers (forwarded to downstream services)</li>
 *   <li>Added to the response headers (returned to the client)</li>
 *   <li>Logged with every gateway log entry for traceability</li>
 * </ul>
 * </p>
 */
@Component
@Slf4j
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Use existing correlation ID or generate a new one
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        log.info("[{}] {} {} -> routing",
                correlationId,
                request.getMethod(),
                request.getURI().getPath());

        // Add to request (forwarded to downstream)
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        // Add to response (returned to client)
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add(CORRELATION_ID_HEADER, correlationId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -200;  // Run first, before JWT filter
    }
}
