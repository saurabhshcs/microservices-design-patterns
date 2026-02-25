package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.api;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class OrderRequest {
    @NotBlank private String customerId;
    @NotBlank private String productId;
    @Min(1) private int quantity;
    @NotNull @DecimalMin("0.01") private BigDecimal amount;
}
