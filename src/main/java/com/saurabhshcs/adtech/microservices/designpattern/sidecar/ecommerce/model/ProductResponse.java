package com.saurabhshcs.adtech.microservices.designpattern.sidecar.ecommerce.model;

import java.math.BigDecimal;

public record ProductResponse(String productId, String name, BigDecimal price,
                               int stock, boolean available) {
    public static ProductResponse from(ProductRequest req) {
        return new ProductResponse(req.productId(), req.name(), req.price(),
                req.stock(), req.stock() > 0);
    }
}
