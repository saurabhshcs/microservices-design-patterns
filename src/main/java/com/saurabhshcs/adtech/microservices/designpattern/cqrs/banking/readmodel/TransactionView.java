package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.readmodel;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Data @Builder
public class TransactionView {
    private UUID transactionId;
    private UUID accountId;
    private String type;
    private BigDecimal amount;
    private String description;
    private Instant occurredAt;
}
