package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.Order;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.OrderStatus;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.orchestrator.OrderSagaOrchestrator;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.service.InventoryService;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.service.PaymentService;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.service.ShippingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
class OrderSagaOrchestratorTest {
    private OrderSagaOrchestrator orchestrator;
    private InventoryService inventoryService;
    @BeforeEach void setUp() {
        PaymentService paymentService = new PaymentService();
        inventoryService = new InventoryService();
        ShippingService shippingService = new ShippingService();
        orchestrator = new OrderSagaOrchestrator(paymentService, inventoryService, shippingService);
    }
    @Test void successfulOrderSaga() {
        Order order = Order.create("CUST-001", "PROD-001", 5, new BigDecimal("99.99"));
        Order result = orchestrator.processOrder(order);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }
    @Test void paymentExceedsLimit_triggersCompensation() {
        Order order = Order.create("CUST-001", "PROD-001", 1, new BigDecimal("15000"));
        Order result = orchestrator.processOrder(order);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPENSATION_COMPLETED);
        assertThat(result.getFailureReason()).contains("exceeds limit");
    }
    @Test void outOfStock_triggersCompensation() {
        Order order = Order.create("CUST-001", "PROD-003", 1, new BigDecimal("99.99"));
        Order result = orchestrator.processOrder(order);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPENSATION_COMPLETED);
    }
    @Test void insufficientQuantity_triggersCompensation() {
        Order order = Order.create("CUST-001", "PROD-002", 30, new BigDecimal("99.99"));
        Order result = orchestrator.processOrder(order);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPENSATION_COMPLETED);
    }
    @Test void successfulOrder_reducesInventory() {
        int stockBefore = inventoryService.getAvailableStock("PROD-001");
        Order order = Order.create("CUST-001", "PROD-001", 10, new BigDecimal("99.99"));
        orchestrator.processOrder(order);
        assertThat(inventoryService.getAvailableStock("PROD-001")).isEqualTo(stockBefore - 10);
    }
}
