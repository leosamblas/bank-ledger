package com.bankledger.commons.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InboxEventIdTest {

    @Test
    @DisplayName("should test equality, hash code, getters and setters of InboxEventId")
    void testInboxEventIdBasics() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        String handlerA = "HandlerA";
        String handlerB = "HandlerB";

        InboxEventId key1 = new InboxEventId(id1, handlerA);
        InboxEventId key2 = new InboxEventId(id1, handlerA);
        InboxEventId key3 = new InboxEventId(id2, handlerA);
        InboxEventId key4 = new InboxEventId(id1, handlerB);

        assertThat(key1)
                .isEqualTo(key2)
                .hasSameHashCodeAs(key2)
                .isNotEqualTo(key3)
                .isNotEqualTo(key4)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object());

        assertThat(key1.getEventId()).isEqualTo(id1);
        assertThat(key1.getHandler()).isEqualTo(handlerA);
        assertThat(key1.toString()).contains(id1.toString(), handlerA);

        InboxEventId empty = new InboxEventId();
        empty.setEventId(id2);
        empty.setHandler(handlerB);
        assertThat(empty.getEventId()).isEqualTo(id2);
        assertThat(empty.getHandler()).isEqualTo(handlerB);
    }
}
