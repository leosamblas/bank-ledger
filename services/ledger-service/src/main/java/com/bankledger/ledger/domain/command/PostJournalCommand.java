package com.bankledger.ledger.domain.command;

import com.bankledger.commons.constant.BankingConstants;
import com.bankledger.commons.events.v1.ledger.PostJournalCommandPayload;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain command requesting the posting of a double-entry transaction.
 */
public record PostJournalCommand(
        UUID commandId,
        UUID transactionId,
        String type,
        long amount,
        String currency,
        UUID sourceAccountId,
        UUID destinationAccountId,
        String terminalId,
        UUID correlationId,
        Instant timestamp
) implements Serializable {

    public static final String DEFAULT_CURRENCY = BankingConstants.DEFAULT_CURRENCY;

    public PostJournalCommand {
        if (commandId == null) {
            commandId = UUID.randomUUID();
        }
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero, was: " + amount);
        }
        if (currency == null || currency.isBlank()) {
            currency = DEFAULT_CURRENCY;
        }
        if (correlationId == null) {
            correlationId = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public PostJournalCommandPayload toPayload() {
        return new PostJournalCommandPayload(
                commandId,
                transactionId,
                type,
                amount,
                currency,
                sourceAccountId,
                destinationAccountId,
                terminalId,
                correlationId,
                timestamp
        );
    }

    public static PostJournalCommand fromPayload(PostJournalCommandPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        return new PostJournalCommand(
                payload.commandId(),
                payload.transactionId(),
                payload.type(),
                payload.amount(),
                payload.currency(),
                payload.sourceAccountId(),
                payload.destinationAccountId(),
                payload.terminalId(),
                payload.correlationId(),
                payload.timestamp()
        );
    }
}
