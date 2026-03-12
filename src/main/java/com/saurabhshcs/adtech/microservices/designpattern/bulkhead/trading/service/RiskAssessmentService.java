package com.saurabhshcs.adtech.microservices.designpattern.bulkhead.trading.service;

import com.saurabhshcs.adtech.microservices.designpattern.bulkhead.trading.model.TradeRequest;
import org.springframework.stereotype.Service;

/**
 * Simulates a risk assessment service that may be slow under load.
 * Isolated behind a Bulkhead so that slow risk checks cannot saturate
 * the trading engine's thread pool.
 */
@Service
public class RiskAssessmentService {

    private static final java.math.BigDecimal MAX_TRADE_VALUE = new java.math.BigDecimal("1000000");

    public boolean isWithinRiskLimits(TradeRequest request) {
        java.math.BigDecimal tradeValue = request.price().multiply(java.math.BigDecimal.valueOf(request.quantity()));
        return tradeValue.compareTo(MAX_TRADE_VALUE) <= 0;
    }
}
