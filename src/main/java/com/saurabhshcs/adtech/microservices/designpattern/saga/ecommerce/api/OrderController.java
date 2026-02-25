package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.api;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.Order;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.OrderStatus;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.orchestrator.OrderSagaOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/orders") @RequiredArgsConstructor
public class OrderController {
    private final OrderSagaOrchestrator orchestrator;
    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderRequest request) {
        Order order = Order.create(request.getCustomerId(), request.getProductId(),
                request.getQuantity(), request.getAmount());
        Order result = orchestrator.processOrder(order);
        int status = result.getStatus() == OrderStatus.COMPLETED ? 201 : 422;
        return ResponseEntity.status(status).body(result);
    }
}
