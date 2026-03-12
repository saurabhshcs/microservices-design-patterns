package com.saurabhshcs.adtech.microservices.designpattern.bulkhead.trading.model;

import java.math.BigDecimal;
import java.util.UUID;

public record TradeRequest(UUID tradeId, String traderId, String symbol,
                           int quantity, BigDecimal price, TradeType type) {
    public enum TradeType { BUY, SELL }

    public static TradeRequest buy(String traderId, String symbol, int qty, BigDecimal price) {
        return new TradeRequest(UUID.randomUUID(), traderId, symbol, qty, price, TradeType.BUY);
    }
    public static TradeRequest sell(String traderId, String symbol, int qty, BigDecimal price) {
        return new TradeRequest(UUID.randomUUID(), traderId, symbol, qty, price, TradeType.SELL);
    }
}
