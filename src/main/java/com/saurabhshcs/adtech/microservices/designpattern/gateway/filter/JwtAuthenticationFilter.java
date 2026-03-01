package com.saurabhshcs.adtech.microservices.designpattern.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global gateway filter that validates JWT Bearer tokens.
 * <p>
 * For every incoming request (except whitelisted paths), this filter:
 * <ol>
 *   <li>Extracts the Bearer token from the Authorization header.</li>
 *   <li>Validates the token signature, expiry, and issuer using Spring Security's
 *       {@link JwtDecoder}.</li>
 *   <li>Extracts claims (user ID, roles) and adds them as headers for
 *       downstream services.</li>
 *   <li>Rejects invalid/missing tokens with HTTP 401.</li>
 * </ol>
 * </p>
 *
 * <p>This filter runs at order {@code -100} to ensure it executes before
 * any route-specific filters (rate limiter, circuit breaker).</p>
 */
@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtDecoder jwtDecoder;

    /** Paths that do not require authentication (e.g., health checks, public catalog) */
    @Value("${gateway.auth.whitelist:/api/products/public/**,/actuator/health}")
    private List<String> whitelistedPaths;

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Skip authentication for whitelisted paths
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // Extract Bearer token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or malformed Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            // Validate the JWT
            Jwt jwt = jwtDecoder.decode(token);

            // Extract claims and propagate as headers to downstream services
            String userId = jwt.getSubject();
            String roles = String.join(",",
                    jwt.getClaimAsStringList("roles") != null
                            ? jwt.getClaimAsStringList("roles")
                            : List.of());

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-ID", userId)
                    .header("X-User-Roles", roles)
                    .header("X-Auth-Token-Expiry", jwt.getExpiresAt().toString())
                    .build();

            log.debug("JWT validated for user {} with roles [{}]", userId, roles);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100;  // Run before rate limiter and circuit breaker
    }

    private boolean isWhitelisted(String path) {
        return whitelistedPaths.stream().anyMatch(pattern -> {
            String regex = pattern.replace("**", ".*").replace("*", "[^/]*");
            return path.matches(regex);
        });
    }
}
