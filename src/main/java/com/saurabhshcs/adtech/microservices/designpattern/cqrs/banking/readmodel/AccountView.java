package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.readmodel;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;
@Data @Builder
public class AccountView {
    private UUID accountId;
    private String ownerId;
    private String ownerName;
    private BigDecimal balance;
    private boolean active;
    private int transactionCount;
}
