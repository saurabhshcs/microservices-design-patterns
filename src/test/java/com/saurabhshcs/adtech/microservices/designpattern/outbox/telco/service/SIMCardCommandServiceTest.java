package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.command.SIMCardCommand;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.domain.SIMActivationStatus;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.domain.SIMCard;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.InMemoryOutboxRepository;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxMessage;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxStatus;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.repository.InMemorySIMCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SIMCardCommandService}.
 *
 * <p>Uses real in-memory repositories — no mocking needed because the repositories are
 * trivial and deterministic. This mirrors the testing convention used across all patterns
 * in this project.
 */
@DisplayName("SIMCardCommandService")
class SIMCardCommandServiceTest {

    private InMemorySIMCardRepository simCardRepository;
    private InMemoryOutboxRepository outboxRepository;
    private SIMCardCommandService commandService;

    @BeforeEach
    void setUp() {
        simCardRepository = new InMemorySIMCardRepository();
        outboxRepository  = new InMemoryOutboxRepository();
        commandService    = new SIMCardCommandService(simCardRepository, outboxRepository, new ObjectMapper());
    }

    // =========================================================================
    // RegisterSIM
    // =========================================================================

    @Nested
    @DisplayName("RegisterSIM command")
    class RegisterSIM {

        @Test
        @DisplayName("registers SIM in PENDING state and writes one outbox message")
        void registersSimAndWritesOutboxEntry() {
            SIMCard sim = commandService.handle(
                    new SIMCardCommand.RegisterSIMCommand("8991000001234567891", "+447911123456", "CUST-001"));

            assertThat(sim.getStatus()).isEqualTo(SIMActivationStatus.PENDING);
            assertThat(sim.getIccid()).isEqualTo("8991000001234567891");
            assertThat(sim.getMsisdn()).isEqualTo("+447911123456");
            assertThat(sim.getCustomerId()).isEqualTo("CUST-001");
            assertThat(sim.getSimId()).isNotBlank();

            List<OutboxMessage> pending = outboxRepository.findPendingMessages();
            assertThat(pending).hasSize(1);
            assertThat(pending.get(0).getEventType()).isEqualTo("SIMRegisteredEvent");
            assertThat(pending.get(0).getAggregateType()).isEqualTo("SIMCard");
            assertThat(pending.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(pending.get(0).getPayload()).contains("8991000001234567891");
        }

        @Test
        @DisplayName("SIM is persisted in the repository after registration")
        void simIsPersistedAfterRegistration() {
            SIMCard sim = commandService.handle(
                    new SIMCardCommand.RegisterSIMCommand("ICC-XYZ", "+1234567890", "CUST-002"));

            assertThat(simCardRepository.findById(sim.getSimId())).isPresent();
        }

        @Test
        @DisplayName("rejects blank ICCID")
        void rejectsBlankIccid() {
            assertThatThrownBy(() -> commandService.handle(
                    new SIMCardCommand.RegisterSIMCommand("", "+447911123456", "CUST-001")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ICCID");
        }

        @Test
        @DisplayName("rejects blank MSISDN")
        void rejectsBlankMsisdn() {
            assertThatThrownBy(() -> commandService.handle(
                    new SIMCardCommand.RegisterSIMCommand("ICC-001", "", "CUST-001")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MSISDN");
        }

        @Test
        @DisplayName("rejects blank customer ID")
        void rejectsBlankCustomerId() {
            assertThatThrownBy(() -> commandService.handle(
                    new SIMCardCommand.RegisterSIMCommand("ICC-001", "+447911123456", "")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Customer ID");
        }

        @Test
        @DisplayName("null fields are rejected")
        void rejectsNullFields() {
            assertThatThrownBy(() -> commandService.handle(
                    new SIMCardCommand.RegisterSIMCommand(null, "+447911123456", "CUST-001")))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> commandService.handle(
                    new SIMCardCommand.RegisterSIMCommand("ICC-001", null, "CUST-001")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // ActivateSIM
    // =========================================================================

    @Nested
    @DisplayName("ActivateSIM command")
    class ActivateSIM {

        @Test
        @DisplayName("activates a PENDING SIM and writes SIMActivatedEvent to outbox")
        void activatesPendingSimAndWritesEvent() {
            SIMCard sim = registerSIM();
            clearPendingOutbox();

            SIMCard activated = commandService.handle(new SIMCardCommand.ActivateSIMCommand(sim.getSimId()));

            assertThat(activated.getStatus()).isEqualTo(SIMActivationStatus.ACTIVE);
            List<OutboxMessage> pending = outboxRepository.findPendingMessages();
            assertThat(pending).hasSize(1);
            assertThat(pending.get(0).getEventType()).isEqualTo("SIMActivatedEvent");
            assertThat(pending.get(0).getAggregateId()).isEqualTo(sim.getSimId());
        }

        @Test
        @DisplayName("state change is persisted in the repository")
        void stateChangeIsPersistedInRepository() {
            SIMCard sim = registerSIM();
            commandService.handle(new SIMCardCommand.ActivateSIMCommand(sim.getSimId()));

            SIMCard stored = simCardRepository.findById(sim.getSimId()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(SIMActivationStatus.ACTIVE);
        }

        @Test
        @DisplayName("cannot activate an ACTIVE SIM — domain rule enforced")
        void cannotActivateActiveSim() {
            SIMCard sim = registerAndActivateSIM();

            assertThatThrownBy(() -> commandService.handle(new SIMCardCommand.ActivateSIMCommand(sim.getSimId())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot be activated");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when SIM ID is unknown")
        void throwsWhenSimNotFound() {
            assertThatThrownBy(() -> commandService.handle(new SIMCardCommand.ActivateSIMCommand("ghost-id")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SIM card not found");
        }
    }

    // =========================================================================
    // SuspendSIM
    // =========================================================================

    @Nested
    @DisplayName("SuspendSIM command")
    class SuspendSIM {

        @Test
        @DisplayName("suspends an ACTIVE SIM and writes SIMSuspendedEvent with reason to outbox")
        void suspendsActiveSimWithReason() {
            SIMCard sim = registerAndActivateSIM();
            clearPendingOutbox();

            commandService.handle(new SIMCardCommand.SuspendSIMCommand(sim.getSimId(), "Non-payment"));

            SIMCard stored = simCardRepository.findById(sim.getSimId()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(SIMActivationStatus.SUSPENDED);

            List<OutboxMessage> pending = outboxRepository.findPendingMessages();
            assertThat(pending).hasSize(1);
            assertThat(pending.get(0).getEventType()).isEqualTo("SIMSuspendedEvent");
            assertThat(pending.get(0).getPayload()).contains("Non-payment");
        }

        @Test
        @DisplayName("cannot suspend a PENDING SIM")
        void cannotSuspendPendingSim() {
            SIMCard sim = registerSIM();

            assertThatThrownBy(() -> commandService.handle(
                    new SIMCardCommand.SuspendSIMCommand(sim.getSimId(), "fraud")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot be suspended");
        }

        @Test
        @DisplayName("rejects blank suspension reason")
        void rejectsBlankReason() {
            SIMCard sim = registerAndActivateSIM();

            assertThatThrownBy(() -> commandService.handle(
                    new SIMCardCommand.SuspendSIMCommand(sim.getSimId(), "")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason");
        }
    }

    // =========================================================================
    // TerminateSIM
    // =========================================================================

    @Nested
    @DisplayName("TerminateSIM command")
    class TerminateSIM {

        @Test
        @DisplayName("terminates an ACTIVE SIM and writes SIMTerminatedEvent to outbox")
        void terminatesActiveSim() {
            SIMCard sim = registerAndActivateSIM();
            clearPendingOutbox();

            commandService.handle(new SIMCardCommand.TerminateSIMCommand(sim.getSimId(), "Customer request"));

            assertThat(simCardRepository.findById(sim.getSimId()).orElseThrow().getStatus())
                    .isEqualTo(SIMActivationStatus.TERMINATED);
            assertThat(outboxRepository.findPendingMessages().get(0).getEventType())
                    .isEqualTo("SIMTerminatedEvent");
        }

        @Test
        @DisplayName("terminates a SUSPENDED SIM")
        void terminatesSuspendedSim() {
            SIMCard sim = registerAndActivateSIM();
            commandService.handle(new SIMCardCommand.SuspendSIMCommand(sim.getSimId(), "Fraud"));
            clearPendingOutbox();

            commandService.handle(new SIMCardCommand.TerminateSIMCommand(sim.getSimId(), "Fraud confirmed"));

            assertThat(simCardRepository.findById(sim.getSimId()).orElseThrow().getStatus())
                    .isEqualTo(SIMActivationStatus.TERMINATED);
        }

        @Test
        @DisplayName("cannot terminate an already terminated SIM")
        void cannotTerminateAlreadyTerminatedSim() {
            SIMCard sim = registerAndActivateSIM();
            commandService.handle(new SIMCardCommand.TerminateSIMCommand(sim.getSimId(), "reason"));

            assertThatThrownBy(() -> commandService.handle(
                    new SIMCardCommand.TerminateSIMCommand(sim.getSimId(), "again")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already terminated");
        }
    }

    // =========================================================================
    // ReactivateSIM
    // =========================================================================

    @Nested
    @DisplayName("ReactivateSIM command")
    class ReactivateSIM {

        @Test
        @DisplayName("reactivates a SUSPENDED SIM and writes SIMReactivatedEvent to outbox")
        void reactivatesSuspendedSim() {
            SIMCard sim = registerAndActivateSIM();
            commandService.handle(new SIMCardCommand.SuspendSIMCommand(sim.getSimId(), "Non-payment"));
            clearPendingOutbox();

            SIMCard reactivated = commandService.handle(new SIMCardCommand.ReactivateSIMCommand(sim.getSimId()));

            assertThat(reactivated.getStatus()).isEqualTo(SIMActivationStatus.ACTIVE);
            assertThat(outboxRepository.findPendingMessages().get(0).getEventType())
                    .isEqualTo("SIMReactivatedEvent");
        }

        @Test
        @DisplayName("cannot reactivate an ACTIVE SIM")
        void cannotReactivateActiveSim() {
            SIMCard sim = registerAndActivateSIM();

            assertThatThrownBy(() -> commandService.handle(new SIMCardCommand.ReactivateSIMCommand(sim.getSimId())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot be reactivated");
        }

        @Test
        @DisplayName("cannot reactivate a TERMINATED SIM")
        void cannotReactivateTerminatedSim() {
            SIMCard sim = registerAndActivateSIM();
            commandService.handle(new SIMCardCommand.TerminateSIMCommand(sim.getSimId(), "reason"));

            assertThatThrownBy(() -> commandService.handle(new SIMCardCommand.ReactivateSIMCommand(sim.getSimId())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot be reactivated");
        }
    }

    // =========================================================================
    // Outbox payload integrity
    // =========================================================================

    @Nested
    @DisplayName("Outbox payload integrity")
    class OutboxPayloadIntegrity {

        @Test
        @DisplayName("outbox message payload is valid JSON containing simId")
        void payloadIsValidJsonWithSimId() {
            SIMCard sim = registerSIM();
            OutboxMessage msg = outboxRepository.findPendingMessages().get(0);

            assertThat(msg.getPayload()).startsWith("{");
            assertThat(msg.getPayload()).contains(sim.getSimId());
        }

        @Test
        @DisplayName("outbox message has a non-null messageId and createdAt")
        void outboxMessageHasMetadata() {
            registerSIM();
            OutboxMessage msg = outboxRepository.findPendingMessages().get(0);

            assertThat(msg.getMessageId()).isNotBlank();
            assertThat(msg.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("each command produces exactly one outbox message")
        void eachCommandProducesOneOutboxMessage() {
            SIMCard sim = registerSIM();
            assertThat(outboxRepository.findPendingMessages()).hasSize(1);

            clearPendingOutbox();
            commandService.handle(new SIMCardCommand.ActivateSIMCommand(sim.getSimId()));
            assertThat(outboxRepository.findPendingMessages()).hasSize(1);
        }
    }

    // =========================================================================
    // Test helpers
    // =========================================================================

    private SIMCard registerSIM() {
        return commandService.handle(new SIMCardCommand.RegisterSIMCommand(
                "8991000001234567891", "+447911123456", "CUST-001"));
    }

    private SIMCard registerAndActivateSIM() {
        SIMCard sim = registerSIM();
        return commandService.handle(new SIMCardCommand.ActivateSIMCommand(sim.getSimId()));
    }

    private void clearPendingOutbox() {
        outboxRepository.findPendingMessages().forEach(m -> {
            m.markPublished();
            outboxRepository.update(m);
        });
    }
}
