package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.command.SIMCardCommand;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.domain.SIMActivationStatus;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.domain.SIMCard;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.InMemoryOutboxRepository;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxStatus;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.publisher.InMemoryMessageBrokerPublisher;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.relay.OutboxRelayService;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.repository.InMemorySIMCardRepository;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.service.SIMCardCommandService;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.service.SIMCardQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for the Transactional Outbox pattern.
 *
 * <p>Wires up the full stack with in-memory implementations and exercises the complete flow:
 * <ol>
 *   <li>Business command → aggregate mutation + outbox write (atomic)</li>
 *   <li>Relay polling → broker publish → outbox status updated</li>
 *   <li>Downstream state visible via query service</li>
 * </ol>
 *
 * <p>No Spring context is needed — the test manages dependencies explicitly.
 */
@DisplayName("SIMCard Transactional Outbox — Integration Tests")
class SIMCardTransactionalOutboxIntegrationTest {

    private InMemorySIMCardRepository      simCardRepository;
    private InMemoryOutboxRepository       outboxRepository;
    private InMemoryMessageBrokerPublisher broker;
    private SIMCardCommandService          commandService;
    private OutboxRelayService             relayService;
    private SIMCardQueryService            queryService;

    @BeforeEach
    void setUp() {
        simCardRepository = new InMemorySIMCardRepository();
        outboxRepository  = new InMemoryOutboxRepository();
        broker            = new InMemoryMessageBrokerPublisher();
        commandService    = new SIMCardCommandService(simCardRepository, outboxRepository, new ObjectMapper());
        relayService      = new OutboxRelayService(outboxRepository, broker);
        queryService      = new SIMCardQueryService(simCardRepository, outboxRepository);
    }

    // =========================================================================
    // Full lifecycle
    // =========================================================================

    @Test
    @DisplayName("full SIM lifecycle: register → activate → suspend → reactivate → terminate")
    void fullSIMLifecycle() {
        // 1. Register
        SIMCard sim = commandService.handle(new SIMCardCommand.RegisterSIMCommand(
                "8991001234567890123", "+447911000001", "CUST-101"));

        assertThat(sim.getStatus()).isEqualTo(SIMActivationStatus.PENDING);
        assertThat(outboxRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(1);

        relayPendingAndAssertEvent("SIMRegisteredEvent", 1);

        // 2. Activate
        commandService.handle(new SIMCardCommand.ActivateSIMCommand(sim.getSimId()));
        assertThat(queryService.findById(sim.getSimId()).getStatus()).isEqualTo(SIMActivationStatus.ACTIVE);

        relayPendingAndAssertEvent("SIMActivatedEvent", 2);

        // 3. Suspend (non-payment)
        commandService.handle(new SIMCardCommand.SuspendSIMCommand(sim.getSimId(), "Non-payment"));
        assertThat(queryService.findById(sim.getSimId()).getStatus()).isEqualTo(SIMActivationStatus.SUSPENDED);

        relayPendingAndAssertEvent("SIMSuspendedEvent", 3);

        // 4. Reactivate
        commandService.handle(new SIMCardCommand.ReactivateSIMCommand(sim.getSimId()));
        assertThat(queryService.findById(sim.getSimId()).getStatus()).isEqualTo(SIMActivationStatus.ACTIVE);

        relayPendingAndAssertEvent("SIMReactivatedEvent", 4);

        // 5. Terminate
        commandService.handle(new SIMCardCommand.TerminateSIMCommand(sim.getSimId(), "Customer request"));
        assertThat(queryService.findById(sim.getSimId()).getStatus()).isEqualTo(SIMActivationStatus.TERMINATED);

        relayPendingAndAssertEvent("SIMTerminatedEvent", 5);

        // Final state: all 5 outbox messages published, none pending
        assertThat(outboxRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(0);
        assertThat(outboxRepository.countByStatus(OutboxStatus.PUBLISHED)).isEqualTo(5);
        assertThat(broker.getPublishedMessages()).hasSize(5);
    }

    // =========================================================================
    // Atomicity guarantee
    // =========================================================================

    @Test
    @DisplayName("SIM state and outbox message are both present before relay runs (atomicity)")
    void simStateAndOutboxBothPresentBeforeRelay() {
        SIMCard sim = commandService.handle(new SIMCardCommand.RegisterSIMCommand(
                "ICC-ATOMIC-TEST", "+447700000001", "CUST-200"));

        // Both persisted — before relay has run
        assertThat(simCardRepository.findById(sim.getSimId())).isPresent();
        assertThat(outboxRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(1);

        // Broker has received nothing yet
        assertThat(broker.getPublishedMessages()).isEmpty();
    }

    // =========================================================================
    // Idempotency
    // =========================================================================

    @Test
    @DisplayName("running the relay twice does not re-publish the same message")
    void relayIsIdempotent() {
        commandService.handle(new SIMCardCommand.RegisterSIMCommand(
                "ICC-IDEMPOTENCY", "+447700000002", "CUST-201"));

        int firstRun  = relayService.relayPendingMessages();
        int secondRun = relayService.relayPendingMessages();

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isEqualTo(0);
        assertThat(broker.getPublishedMessages()).hasSize(1);
    }

    // =========================================================================
    // Multiple SIMs
    // =========================================================================

    @Test
    @DisplayName("batch: activating 3 SIMs produces 3 isolated outbox messages")
    void batchActivationProducesIsolatedOutboxMessages() {
        SIMCard sim1 = commandService.handle(new SIMCardCommand.RegisterSIMCommand("ICC-A", "+447000000001", "CUST-A"));
        SIMCard sim2 = commandService.handle(new SIMCardCommand.RegisterSIMCommand("ICC-B", "+447000000002", "CUST-B"));
        SIMCard sim3 = commandService.handle(new SIMCardCommand.RegisterSIMCommand("ICC-C", "+447000000003", "CUST-C"));

        assertThat(outboxRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(3);

        int relayed = relayService.relayPendingMessages();

        assertThat(relayed).isEqualTo(3);
        assertThat(broker.getPublishedMessages()).hasSize(3);
        assertThat(outboxRepository.countByStatus(OutboxStatus.PUBLISHED)).isEqualTo(3);
    }

    // =========================================================================
    // Query service
    // =========================================================================

    @Test
    @DisplayName("query service returns current SIM state after each command")
    void queryServiceReflectsLatestState() {
        SIMCard sim = commandService.handle(new SIMCardCommand.RegisterSIMCommand(
                "ICC-QUERY", "+447900000099", "CUST-Q1"));

        assertThat(queryService.findById(sim.getSimId()).getStatus()).isEqualTo(SIMActivationStatus.PENDING);

        commandService.handle(new SIMCardCommand.ActivateSIMCommand(sim.getSimId()));
        assertThat(queryService.findById(sim.getSimId()).getStatus()).isEqualTo(SIMActivationStatus.ACTIVE);

        commandService.handle(new SIMCardCommand.SuspendSIMCommand(sim.getSimId(), "Fraud"));
        assertThat(queryService.findById(sim.getSimId()).getStatus()).isEqualTo(SIMActivationStatus.SUSPENDED);
    }

    @Test
    @DisplayName("query service can locate SIM by MSISDN")
    void queryServiceFindsSimByMsisdn() {
        commandService.handle(new SIMCardCommand.RegisterSIMCommand(
                "ICC-MSISDN-TEST", "+447911234567", "CUST-M1"));

        assertThat(queryService.findByMsisdn("+447911234567")).isPresent();
        assertThat(queryService.findByMsisdn("+00000000000")).isEmpty();
    }

    @Test
    @DisplayName("query service pending-outbox count decreases after relay")
    void pendingOutboxCountDecreasesAfterRelay() {
        commandService.handle(new SIMCardCommand.RegisterSIMCommand("ICC-CNT", "+447911000099", "CUST-C1"));

        assertThat(queryService.countOutboxMessagesByStatus(OutboxStatus.PENDING)).isEqualTo(1);

        relayService.relayPendingMessages();

        assertThat(queryService.countOutboxMessagesByStatus(OutboxStatus.PENDING)).isEqualTo(0);
        assertThat(queryService.countOutboxMessagesByStatus(OutboxStatus.PUBLISHED)).isEqualTo(1);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void relayPendingAndAssertEvent(String expectedEventType, int expectedBrokerTotal) {
        int relayed = relayService.relayPendingMessages();
        assertThat(relayed).isEqualTo(1);
        assertThat(broker.getPublishedMessages()).hasSize(expectedBrokerTotal);
        assertThat(broker.getPublishedMessages().get(expectedBrokerTotal - 1).getEventType())
                .isEqualTo(expectedEventType);
    }
}
