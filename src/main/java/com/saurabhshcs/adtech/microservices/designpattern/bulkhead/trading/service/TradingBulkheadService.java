package com.saurabhshcs.adtech.microservices.designpattern.bulkhead.trading.service;

import com.saurabhshcs.adtech.microservices.designpattern.bulkhead.trading.model.TradeRequest;
import com.saurabhshcs.adtech.microservices.designpattern.bulkhead.trading.model.TradeResult;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Bulkhead pattern — Financial / Trading domain.
 *
 * <p>Isolates the risk-assessment path from the main trade-execution path using a
 * Resilience4j semaphore Bulkhead. If the risk service is saturated (too many
 * concurrent risk checks), new requests are rejected immediately rather than queuing
 * indefinitely and degrading the entire trading platform.</p>
 *
 * <pre>
 *   Trader Request
 *       │
 *       ▼
 *   TradingBulkheadService
 *       │
 *       ├──[Bulkhead permits available]──► RiskAssessmentService ──► execute trade
 *       │
 *       └──[Bulkhead FULL]────────────────► Reject: "System at capacity"
 * </pre>
 */
@Slf4j
@Service
public class TradingBulkheadService {

    private static final String BULKHEAD_NAME = "riskAssessment";

    private final RiskAssessmentService riskService;
    private final Bulkhead bulkhead;

    public TradingBulkheadService(RiskAssessmentService riskService) {
        this.riskService = riskService;
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(10)
                .maxWaitDuration(Duration.ofMillis(500))
                .build();
        this.bulkhead = BulkheadRegistry.of(config).bulkhead(BULKHEAD_NAME);
    }

    public TradeResult executeTrade(TradeRequest request) {
        Supplier<TradeResult> decorated = Bulkhead.decorateSupplier(bulkhead, () -> {
            log.info("Risk check for trade {} ({})", request.tradeId(), request.type());
            boolean withinLimits = riskService.isWithinRiskLimits(request);
            if (!withinLimits) {
                log.warn("Trade {} rejected: exceeds risk limits", request.tradeId());
                return TradeResult.rejected(request.tradeId(), "Trade value exceeds risk limit");
            }
            log.info("Trade {} executed successfully", request.tradeId());
            return TradeResult.executed(request.tradeId());
        });

        try {
            return decorated.get();
        } catch (BulkheadFullException ex) {
            log.warn("Bulkhead full — trade {} rejected: {}", request.tradeId(), ex.getMessage());
            return TradeResult.rejected(request.tradeId(), "System at capacity — please retry");
        }
    }

    public Bulkhead.Metrics metrics() {
        return bulkhead.getMetrics();
    }
}
