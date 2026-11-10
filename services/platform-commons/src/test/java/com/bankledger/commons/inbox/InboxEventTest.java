package com.bankledger.commons.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InboxEventTest {

    @Test
    @DisplayName("should test constructors, getters, setters, equals, and hashCode of InboxEvent")
    void testInboxEventBasics() {
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        String handler1 = "TransferConsumer";
        String handler2 = "AuditConsumer";
        Instant now = Instant.now();

        InboxEvent event1 = new InboxEvent(eventId1, handler1, now);
        InboxEvent event2 = new InboxEvent(eventId1, handler1, now.plusSeconds(10));
        InboxEvent event3 = new InboxEvent(eventId2, handler1, now);
        InboxEvent event4 = new InboxEvent(eventId1, handler2, now);

        assertThat(event1)
                .isEqualTo(event2)
                .hasSameHashCodeAs(event2)
                .isNotEqualTo(event3)
                .isNotEqualTo(event4)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object());

        assertThat(event1.getEventId()).isEqualTo(eventId1);
        assertThat(event1.getHandler()).isEqualTo(handler1);
        assertThat(event1.getProcessedAt()).isEqualTo(now);
        assertThat(event1.toString()).contains(eventId1.toString(), handler1);

        InboxEvent defaultConstructorEvent = new InboxEvent();
        defaultConstructorEvent.setEventId(eventId2);
        defaultConstructorEvent.setHandler(handler2);
        defaultConstructorEvent.setProcessedAt(now);

        assertThat(defaultConstructorEvent.getEventId()).isEqualTo(eventId2);
        assertThat(defaultConstructorEvent.getHandler()).isEqualTo(handler2);
        assertThat(defaultConstructorEvent.getProcessedAt()).isEqualTo(now);

        InboxEvent twoArgConstructorEvent = new InboxEvent(eventId1, handler1);
        assertThat(twoArgConstructorEvent.getEventId()).isEqualTo(eventId1);
        assertThat(twoArgConstructorEvent.getHandler()).isEqualTo(handler1);
        assertThat(twoArgConstructorEvent.getProcessedAt()).isNotNull();
    }
}
