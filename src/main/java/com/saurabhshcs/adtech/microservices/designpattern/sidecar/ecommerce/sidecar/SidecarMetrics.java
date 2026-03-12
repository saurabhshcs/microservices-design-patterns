package com.saurabhshcs.adtech.microservices.designpattern.sidecar.ecommerce.sidecar;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Sidecar pattern — eCommerce Product Service.
 *
 * <p>A Sidecar runs alongside the main service container and handles
 * cross-cutting concerns (metrics, logging, health) without changing
 * the main service code. This mirrors how service-mesh sidecars
 * (Envoy, Linkerd) work in Kubernetes.</p>
 *
 * <p>This class is the sidecar: it collects call counts and latency
 * transparently as the main {@code ProductService} delegates through it.</p>
 */
public class SidecarMetrics {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors   = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);

    public void recordRequest(long latencyMs, boolean success) {
        totalRequests.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
        if (!success) totalErrors.incrementAndGet();
    }

    public long totalRequests()  { return totalRequests.get(); }
    public long totalErrors()    { return totalErrors.get(); }
    public double avgLatencyMs() {
        long reqs = totalRequests.get();
        return reqs == 0 ? 0.0 : (double) totalLatencyMs.get() / reqs;
    }
    public double errorRate() {
        long reqs = totalRequests.get();
        return reqs == 0 ? 0.0 : (double) totalErrors.get() / reqs;
    }

    public void reset() {
        totalRequests.set(0);
        totalErrors.set(0);
        totalLatencyMs.set(0);
    }
}
