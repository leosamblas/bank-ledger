package com.bankledger.commons.inbox;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@link InboxEvent}, combining {@code eventId} and {@code handler}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class InboxEventId implements Serializable {

    private UUID eventId;
    private String handler;
}
