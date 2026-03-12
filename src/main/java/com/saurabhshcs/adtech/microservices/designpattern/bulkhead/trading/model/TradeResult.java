package com.saurabhshcs.adtech.microservices.designpattern.bulkhead.trading.model;

import java.util.UUID;

public record TradeResult(UUID tradeId, String status, String message) {
    public static TradeResult executed(UUID id) {
        return new TradeResult(id, "EXECUTED", "Trade executed successfully");
    }
    public static TradeResult rejected(UUID id, String reason) {
        return new TradeResult(id, "REJECTED", reason);
    }
}
