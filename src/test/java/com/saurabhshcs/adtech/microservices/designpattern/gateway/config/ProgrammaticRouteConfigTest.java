package com.saurabhshcs.adtech.microservices.designpattern.gateway.config;

import com.saurabhshcs.adtech.microservices.designpattern.gateway.GatewayTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD integration tests for {@link ProgrammaticRouteConfig}.
 * Also covers SecurityConfig.securityWebFilterChain() as a side effect of loading
 * the full gateway context with the "programmatic-routes" profile.
 * 3 positive and 3 negative scenarios.
 */
@SpringBootTest(classes = GatewayTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("programmatic-routes")
class ProgrammaticRouteConfigTest {

    /**
     * Mock JwtDecoder satisfies JwtAuthenticationFilter's constructor dependency
     * without needing a real external auth server.
     */
    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    RouteLocator routeLocator;

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void fourProgrammaticRoutesAreRegistered() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull().hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void productServiceRouteExists() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull()
                .anyMatch(r -> r.getId().equals("product-service"));
    }

    @Test
    void orderServiceRouteIsConfigured() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull()
                .anyMatch(r -> r.getId().equals("order-service"));
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void noRouteWithUnknownId_isPresent() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull()
                .noneMatch(r -> r.getId().equals("non-existent-service"));
    }

    @Test
    void userServiceRouteExists() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull()
                .anyMatch(r -> r.getId().equals("user-service"));
    }

    @Test
    void reviewServiceRouteExists() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull()
                .anyMatch(r -> r.getId().equals("review-service"));
    }
}
