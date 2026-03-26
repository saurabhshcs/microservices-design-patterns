package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.command.SIMCardCommand;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.domain.SIMCard;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.InMemoryOutboxRepository;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxStatus;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.repository.InMemorySIMCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SIMCardQueryService")
class SIMCardQueryServiceTest {

    private InMemorySIMCardRepository simCardRepository;
    private InMemoryOutboxRepository  outboxRepository;
    private SIMCardCommandService     commandService;
    private SIMCardQueryService       queryService;

    @BeforeEach
    void setUp() {
        simCardRepository = new InMemorySIMCardRepository();
        outboxRepository  = new InMemoryOutboxRepository();
        commandService    = new SIMCardCommandService(simCardRepository, outboxRepository, new ObjectMapper());
        queryService      = new SIMCardQueryService(simCardRepository, outboxRepository);
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findById returns the correct SIM")
    void findByIdReturnsCorrectSim() {
        SIMCard sim = register();

        SIMCard found = queryService.findById(sim.getSimId());

        assertThat(found.getSimId()).isEqualTo(sim.getSimId());
        assertThat(found.getMsisdn()).isEqualTo("+447911123456");
    }

    @Test
    @DisplayName("findById throws when SIM does not exist")
    void findByIdThrowsWhenNotFound() {
        assertThatThrownBy(() -> queryService.findById("non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIM card not found");
    }

    // -------------------------------------------------------------------------
    // findByMsisdn
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByMsisdn returns the SIM for a known number")
    void findByMsisdnReturnsSimForKnownNumber() {
        register();

        Optional<SIMCard> found = queryService.findByMsisdn("+447911123456");

        assertThat(found).isPresent();
        assertThat(found.get().getMsisdn()).isEqualTo("+447911123456");
    }

    @Test
    @DisplayName("findByMsisdn returns empty for an unknown number")
    void findByMsisdnReturnsEmptyForUnknownNumber() {
        assertThat(queryService.findByMsisdn("+00000000000")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findByIccid
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByIccid returns the SIM for a known ICCID")
    void findByIccidReturnsSimForKnownIccid() {
        register();

        Optional<SIMCard> found = queryService.findByIccid("8991000001234567891");

        assertThat(found).isPresent();
        assertThat(found.get().getIccid()).isEqualTo("8991000001234567891");
    }

    @Test
    @DisplayName("findByIccid returns empty for an unknown ICCID")
    void findByIccidReturnsEmptyForUnknownIccid() {
        assertThat(queryService.findByIccid("UNKNOWN-ICCID")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Outbox queries
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findPendingOutboxMessages returns only PENDING entries")
    void findPendingOutboxMessagesReturnsPendingOnly() {
        register();

        assertThat(queryService.findPendingOutboxMessages()).hasSize(1);
        assertThat(queryService.findPendingOutboxMessages().get(0).getStatus())
                .isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("findAllOutboxMessages returns all entries regardless of status")
    void findAllOutboxMessagesReturnsAll() {
        SIMCard sim = register();
        commandService.handle(new SIMCardCommand.ActivateSIMCommand(sim.getSimId()));
        // mark first message published
        outboxRepository.findPendingMessages().stream().findFirst().ifPresent(m -> {
            m.markPublished();
            outboxRepository.update(m);
        });

        assertThat(queryService.findAllOutboxMessages()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("countOutboxMessagesByStatus counts correctly per status")
    void countOutboxMessagesByStatusIsAccurate() {
        register();
        register2();

        assertThat(queryService.countOutboxMessagesByStatus(OutboxStatus.PENDING)).isEqualTo(2);
        assertThat(queryService.countOutboxMessagesByStatus(OutboxStatus.PUBLISHED)).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private SIMCard register() {
        return commandService.handle(new SIMCardCommand.RegisterSIMCommand(
                "8991000001234567891", "+447911123456", "CUST-001"));
    }

    private SIMCard register2() {
        return commandService.handle(new SIMCardCommand.RegisterSIMCommand(
                "8991000009999999999", "+447911999999", "CUST-002"));
    }
}
