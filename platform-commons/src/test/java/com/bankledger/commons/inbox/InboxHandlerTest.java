package com.bankledger.commons.inbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboxHandlerTest {

    @Mock
    private InboxEventRepository repository;

    private InboxHandler inboxHandler;

    @BeforeEach
    void setUp() {
        inboxHandler = new InboxHandler(repository);
    }

    @Test
    @DisplayName("should execute business logic and persist inbox event when event is not processed")
    void shouldExecuteAndPersistWhenNotProcessed() {
        UUID eventId = UUID.randomUUID();
        String handler = "AccountOpenedHandler";
        when(repository.existsByEventIdAndHandler(eventId, handler)).thenReturn(false);

        AtomicBoolean executed = new AtomicBoolean(false);
        inboxHandler.execute(eventId, handler, () -> executed.set(true));

        assertThat(executed.get()).isTrue();

        ArgumentCaptor<InboxEvent> captor = ArgumentCaptor.forClass(InboxEvent.class);
        verify(repository).saveAndFlush(captor.capture());
        InboxEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getHandler()).isEqualTo(handler);
        assertThat(saved.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("should skip execution when event is already processed")
    void shouldSkipWhenAlreadyProcessed() {
        UUID eventId = UUID.randomUUID();
        String handler = "AccountOpenedHandler";
        when(repository.existsByEventIdAndHandler(eventId, handler)).thenReturn(true);

        AtomicBoolean executed = new AtomicBoolean(false);
        inboxHandler.execute(eventId, handler, () -> executed.set(true));

        assertThat(executed.get()).isFalse();
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("should support consumer overload with typed payload")
    void shouldSupportConsumerOverload() {
        UUID eventId = UUID.randomUUID();
        String handler = "PayloadHandler";
        when(repository.existsByEventIdAndHandler(eventId, handler)).thenReturn(false);

        AtomicInteger captured = new AtomicInteger(0);
        inboxHandler.execute(eventId, handler, captured::set, 42);

        assertThat(captured.get()).isEqualTo(42);
        verify(repository).saveAndFlush(any(InboxEvent.class));
    }

    @Test
    @DisplayName("should handle DataIntegrityViolationException gracefully on concurrent save")
    void shouldHandleDataIntegrityViolationGracefully() {
        UUID eventId = UUID.randomUUID();
        String handler = "RaceHandler";
        when(repository.existsByEventIdAndHandler(eventId, handler)).thenReturn(false);
        when(repository.saveAndFlush(any(InboxEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        AtomicBoolean executed = new AtomicBoolean(false);
        // Should not rethrow DataIntegrityViolationException
        inboxHandler.execute(eventId, handler, () -> executed.set(true));

        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("should validate null arguments")
    void shouldValidateNullArguments() {
        UUID eventId = UUID.randomUUID();
        String handler = "TestHandler";

        assertThatThrownBy(() -> inboxHandler.execute(null, handler, () -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventId");

        assertThatThrownBy(() -> inboxHandler.execute(eventId, null, () -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handlerName");

        assertThatThrownBy(() -> inboxHandler.execute(eventId, handler, (Runnable) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("businessLogic");

        assertThatThrownBy(() -> inboxHandler.execute(eventId, handler, null, "payload"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("consumer");
    }
}
