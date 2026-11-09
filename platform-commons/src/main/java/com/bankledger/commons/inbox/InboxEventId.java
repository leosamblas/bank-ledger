package com.bankledger.commons.inbox;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link InboxEvent}, combining {@code eventId} and {@code handler}.
 */
public class InboxEventId implements Serializable {

    private UUID eventId;
    private String handler;

    public InboxEventId() {
    }

    public InboxEventId(UUID eventId, String handler) {
        this.eventId = eventId;
        this.handler = handler;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InboxEventId that = (InboxEventId) o;
        return Objects.equals(eventId, that.eventId) && Objects.equals(handler, that.handler);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, handler);
    }

    @Override
    public String toString() {
        return "InboxEventId{" +
                "eventId=" + eventId +
                ", handler='" + handler + '\'' +
                '}';
    }
}
