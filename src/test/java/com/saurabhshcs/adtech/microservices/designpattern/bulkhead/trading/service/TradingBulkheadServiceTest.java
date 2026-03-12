package com.saurabhshcs.adtech.microservices.designpattern.bulkhead.trading.service;

import com.saurabhshcs.adtech.microservices.designpattern.bulkhead.trading.model.TradeRequest;
import com.saurabhshcs.adtech.microservices.designpattern.bulkhead.trading.model.TradeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD unit tests for {@link TradingBulkheadService}.
 * 3 positive and 3 negative scenarios.
 */
class TradingBulkheadServiceTest {

    private TradingBulkheadService service;

    @BeforeEach
    void setUp() {
        service = new TradingBulkheadService(new RiskAssessmentService());
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void executeTrade_withinRiskLimits_returnsExecuted() {
        TradeRequest request = TradeRequest.buy("TRADER-01", "AAPL", 10, new BigDecimal("150.00"));

        TradeResult result = service.executeTrade(request);

        assertThat(result.status()).isEqualTo("EXECUTED");
    }

    @Test
    void executeTrade_sellOrder_withinLimits_returnsExecuted() {
        TradeRequest request = TradeRequest.sell("TRADER-02", "TSLA", 5, new BigDecimal("200.00"));

        TradeResult result = service.executeTrade(request);

        assertThat(result.status()).isEqualTo("EXECUTED");
        assertThat(result.tradeId()).isEqualTo(request.tradeId());
    }

    @Test
    void executeTrade_atExactRiskLimit_returnsExecuted() {
        // 10,000 shares × £100 = £1,000,000 — exactly at the limit
        TradeRequest request = TradeRequest.buy("TRADER-03", "MSFT", 10000, new BigDecimal("100.00"));

        TradeResult result = service.executeTrade(request);

        assertThat(result.status()).isEqualTo("EXECUTED");
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void executeTrade_exceedingRiskLimit_returnsRejected() {
        // 10,001 shares × £100.01 > £1,000,000
        TradeRequest request = TradeRequest.buy("TRADER-04", "GOOGL", 10001, new BigDecimal("100.01"));

        TradeResult result = service.executeTrade(request);

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.message()).contains("risk limit");
    }

    @Test
    void executeTrade_veryLargeOrder_returnsRejected() {
        TradeRequest request = TradeRequest.sell("TRADER-05", "AMZN", 100000, new BigDecimal("3000.00"));

        TradeResult result = service.executeTrade(request);

        assertThat(result.status()).isEqualTo("REJECTED");
    }

    @Test
    void metrics_afterSuccessfulTrades_showsAvailablePermits() {
        TradeRequest request = TradeRequest.buy("TRADER-06", "NVDA", 1, new BigDecimal("500.00"));
        service.executeTrade(request);

        // After the trade completes, the bulkhead permit should be released
        assertThat(service.metrics().getAvailableConcurrentCalls()).isEqualTo(10);
    }
}
