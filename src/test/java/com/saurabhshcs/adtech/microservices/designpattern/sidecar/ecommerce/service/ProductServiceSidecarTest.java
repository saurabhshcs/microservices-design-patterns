package com.saurabhshcs.adtech.microservices.designpattern.sidecar.ecommerce.service;

import com.saurabhshcs.adtech.microservices.designpattern.sidecar.ecommerce.model.ProductRequest;
import com.saurabhshcs.adtech.microservices.designpattern.sidecar.ecommerce.model.ProductResponse;
import com.saurabhshcs.adtech.microservices.designpattern.sidecar.ecommerce.sidecar.SidecarMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD unit tests for {@link ProductService} with Sidecar pattern.
 * 3 positive and 3 negative scenarios.
 */
class ProductServiceSidecarTest {

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService();
    }

    private ProductRequest laptop() {
        return new ProductRequest("PROD-001", "Laptop Pro", new BigDecimal("1299.99"), 50);
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void registerProduct_returnsProductResponse_withCorrectDetails() {
        ProductResponse response = productService.registerProduct(laptop());

        assertThat(response.productId()).isEqualTo("PROD-001");
        assertThat(response.name()).isEqualTo("Laptop Pro");
        assertThat(response.available()).isTrue();
    }

    @Test
    void findProduct_afterRegistration_returnsProduct() {
        productService.registerProduct(laptop());

        Optional<ProductResponse> found = productService.findProduct("PROD-001");

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Laptop Pro");
    }

    @Test
    void sidecarMetrics_recordsTotalRequests_afterOperations() {
        productService.registerProduct(laptop());
        productService.findProduct("PROD-001");

        SidecarMetrics metrics = productService.getMetrics();
        assertThat(metrics.totalRequests()).isEqualTo(2);
        assertThat(metrics.totalErrors()).isEqualTo(0);
        assertThat(metrics.errorRate()).isEqualTo(0.0);
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void findProduct_forUnknownId_returnsEmpty() {
        Optional<ProductResponse> result = productService.findProduct("UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    void registerProduct_outOfStock_marksUnavailable() {
        ProductRequest outOfStock = new ProductRequest("PROD-002", "Sold Out Item",
                new BigDecimal("99.99"), 0);

        ProductResponse response = productService.registerProduct(outOfStock);

        assertThat(response.available()).isFalse();
        assertThat(response.stock()).isEqualTo(0);
    }

    @Test
    void sidecarMetrics_avgLatency_isNonNegative() {
        productService.registerProduct(laptop());
        productService.findProduct("PROD-001");
        productService.findProduct("UNKNOWN");

        SidecarMetrics metrics = productService.getMetrics();
        assertThat(metrics.avgLatencyMs()).isGreaterThanOrEqualTo(0.0);
        assertThat(metrics.totalRequests()).isEqualTo(3);
    }
}
