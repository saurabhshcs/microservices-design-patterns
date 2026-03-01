package com.saurabhshcs.adtech.microservices.designpattern.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate limiter key resolver configuration.
 * <p>
 * Determines how rate limits are applied. By default, limits are per-user
 * (based on the X-User-ID header set by the JWT filter). For unauthenticated
 * routes, falls back to the client IP address.
 * </p>
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Resolves the rate limit key to the authenticated user's ID.
     * If no user ID is present (unauthenticated route), uses the client IP.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            // Fallback to IP address for unauthenticated routes
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip);
        };
    }

    /**
     * Alternative: per-API-path rate limiting (useful for public APIs).
     */
    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getURI().getPath());
    }
}
