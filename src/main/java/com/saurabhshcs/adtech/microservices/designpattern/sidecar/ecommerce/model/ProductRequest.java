package com.saurabhshcs.adtech.microservices.designpattern.sidecar.ecommerce.model;

import java.math.BigDecimal;

public record ProductRequest(String productId, String name, BigDecimal price, int stock) {}
