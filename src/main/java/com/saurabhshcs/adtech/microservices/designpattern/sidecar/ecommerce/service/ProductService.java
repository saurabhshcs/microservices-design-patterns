package com.saurabhshcs.adtech.microservices.designpattern.sidecar.ecommerce.service;

import com.saurabhshcs.adtech.microservices.designpattern.sidecar.ecommerce.model.ProductRequest;
import com.saurabhshcs.adtech.microservices.designpattern.sidecar.ecommerce.model.ProductResponse;
import com.saurabhshcs.adtech.microservices.designpattern.sidecar.ecommerce.sidecar.LoggingSidecar;
import com.saurabhshcs.adtech.microservices.designpattern.sidecar.ecommerce.sidecar.SidecarMetrics;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main product service — business logic only.
 * Cross-cutting concerns (logging, metrics) are handled by the Sidecar,
 * keeping this class clean and single-responsibility.
 */
@Service
public class ProductService {

    private final Map<String, ProductResponse> catalogue = new ConcurrentHashMap<>();
    private final LoggingSidecar logger = new LoggingSidecar();
    private final SidecarMetrics metrics = new SidecarMetrics();

    public ProductResponse registerProduct(ProductRequest request) {
        long start = System.currentTimeMillis();
        logger.logRequest("registerProduct", request);
        try {
            ProductResponse response = ProductResponse.from(request);
            catalogue.put(request.productId(), response);
            long latency = System.currentTimeMillis() - start;
            metrics.recordRequest(latency, true);
            logger.logResponse("registerProduct", response, latency);
            return response;
        } catch (Exception ex) {
            metrics.recordRequest(System.currentTimeMillis() - start, false);
            logger.logError("registerProduct", ex);
            throw ex;
        }
    }

    public Optional<ProductResponse> findProduct(String productId) {
        long start = System.currentTimeMillis();
        logger.logRequest("findProduct", productId);
        Optional<ProductResponse> result = Optional.ofNullable(catalogue.get(productId));
        long latency = System.currentTimeMillis() - start;
        metrics.recordRequest(latency, true);
        logger.logResponse("findProduct", result.orElse(null), latency);
        return result;
    }

    public SidecarMetrics getMetrics() {
        return metrics;
    }
}
