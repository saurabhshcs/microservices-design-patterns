package com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.model;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountSummary(UUID accountId, String ownerId, String ownerName,
                              BigDecimal balance, String source) {
    /** Identifies which implementation served this response. */
    public boolean isFromLegacy() { return "LEGACY".equals(source); }
    public boolean isFromModern() { return "MODERN".equals(source); }
}
