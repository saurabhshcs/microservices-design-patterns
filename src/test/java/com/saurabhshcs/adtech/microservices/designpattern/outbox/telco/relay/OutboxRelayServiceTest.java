package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.relay;

import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.InMemoryOutboxRepository;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxMessage;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxStatus;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.publisher.MessageBrokerPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OutboxRelayService}.
 *
 * <p>Uses a real {@link InMemoryOutboxRepository} and a mock {@link MessageBrokerPublisher}
 * to drive the relay through happy-path and failure scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayService")
class OutboxRelayServiceTest {

    private InMemoryOutboxRepository outboxRepository;

    @Mock
    private MessageBrokerPublisher messageBrokerPublisher;

    private OutboxRelayService relayService;

    @BeforeEach
    void setUp() {
        outboxRepository = new InMemoryOutboxRepository();
        relayService     = new OutboxRelayService(outboxRepository, messageBrokerPublisher);
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Nested
    @DisplayName("successful relay")
    class SuccessfulRelay {

        @Test
        @DisplayName("relays all pending messages and marks them PUBLISHED")
        void relaysAllPendingMessages() {
            outboxRepository.save(pendingMessage("SIMActivatedEvent"));
            outboxRepository.save(pendingMessage("SIMSuspendedEvent"));

            int count = relayService.relayPendingMessages();

            assertThat(count).isEqualTo(2);
            verify(messageBrokerPublisher, times(2)).publish(any());
            assertThat(outboxRepository.countByStatus(OutboxStatus.PUBLISHED)).isEqualTo(2);
            assertThat(outboxRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 when there are no pending messages")
        void returnsZeroWhenNoPendingMessages() {
            int count = relayService.relayPendingMessages();

            assertThat(count).isEqualTo(0);
            verify(messageBrokerPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("does not relay already-published messages")
        void doesNotRelayAlreadyPublishedMessages() {
            OutboxMessage alreadyPublished = pendingMessage("SIMActivatedEvent");
            alreadyPublished.markPublished();
            outboxRepository.save(alreadyPublished);

            int count = relayService.relayPendingMessages();

            assertThat(count).isEqualTo(0);
            verify(messageBrokerPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("does not relay FAILED messages in the same run")
        void doesNotRelayFailedMessages() {
            OutboxMessage failed = pendingMessage("SIMActivatedEvent");
            failed.markFailed();
            outboxRepository.save(failed);

            int count = relayService.relayPendingMessages();

            assertThat(count).isEqualTo(0);
            verify(messageBrokerPublisher, never()).publish(any());
        }
    }

    // =========================================================================
    // Failure handling
    // =========================================================================

    @Nested
    @DisplayName("broker failure handling")
    class BrokerFailureHandling {

        @Test
        @DisplayName("marks message FAILED when broker throws an exception")
        void marksMessageFailedWhenBrokerThrows() {
            outboxRepository.save(pendingMessage("SIMActivatedEvent"));
            doThrow(new RuntimeException("Kafka unavailable")).when(messageBrokerPublisher).publish(any());

            int count = relayService.relayPendingMessages();

            assertThat(count).isEqualTo(0);
            assertThat(outboxRepository.countByStatus(OutboxStatus.FAILED)).isEqualTo(1);
            assertThat(outboxRepository.countByStatus(OutboxStatus.PUBLISHED)).isEqualTo(0);
        }

        @Test
        @DisplayName("increments retryCount on each failure")
        void incrementsRetryCountOnFailure() {
            OutboxMessage msg = pendingMessage("SIMActivatedEvent");
            outboxRepository.save(msg);
            doThrow(new RuntimeException("broker down")).when(messageBrokerPublisher).publish(any());

            relayService.relayPendingMessages();

            OutboxMessage stored = outboxRepository.findAll().get(0);
            assertThat(stored.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("partially relays when only some messages fail")
        void partiallyRelaysWhenSomeMessagesFail() {
            outboxRepository.save(pendingMessage("SIMActivatedEvent"));
            outboxRepository.save(pendingMessage("SIMSuspendedEvent"));

            // first call throws, second succeeds
            doThrow(new RuntimeException("broker error"))
                    .doNothing()
                    .when(messageBrokerPublisher).publish(any());

            int count = relayService.relayPendingMessages();

            assertThat(count).isEqualTo(1);
            assertThat(outboxRepository.countByStatus(OutboxStatus.PUBLISHED)).isEqualTo(1);
            assertThat(outboxRepository.countByStatus(OutboxStatus.FAILED)).isEqualTo(1);
        }

        @Test
        @DisplayName("relay continues processing remaining messages after one failure")
        void continuesAfterFailure() {
            // 3 messages; first one will fail
            outboxRepository.save(pendingMessage("SIMRegisteredEvent"));
            outboxRepository.save(pendingMessage("SIMActivatedEvent"));
            outboxRepository.save(pendingMessage("SIMTerminatedEvent"));

            doThrow(new RuntimeException("transient error"))
                    .doNothing()
                    .doNothing()
                    .when(messageBrokerPublisher).publish(any());

            int count = relayService.relayPendingMessages();

            assertThat(count).isEqualTo(2);
            assertThat(outboxRepository.countByStatus(OutboxStatus.PUBLISHED)).isEqualTo(2);
            assertThat(outboxRepository.countByStatus(OutboxStatus.FAILED)).isEqualTo(1);
        }
    }

    // =========================================================================
    // Idempotency
    // =========================================================================

    @Nested
    @DisplayName("idempotency")
    class Idempotency {

        @Test
        @DisplayName("re-running the relay after all messages published produces no further publishes")
        void reRunningRelayIsNoop() {
            outboxRepository.save(pendingMessage("SIMActivatedEvent"));

            relayService.relayPendingMessages();          // first run
            int secondRun = relayService.relayPendingMessages(); // second run

            assertThat(secondRun).isEqualTo(0);
            verify(messageBrokerPublisher, times(1)).publish(any());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private OutboxMessage pendingMessage(String eventType) {
        return OutboxMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .aggregateId(UUID.randomUUID().toString())
                .aggregateType("SIMCard")
                .eventType(eventType)
                .payload("{\"simId\":\"test-sim\"}")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }
}
