package com.bankledger.ledger.domain;

import com.bankledger.ledger.domain.command.PostJournalCommand;
import com.bankledger.ledger.domain.event.EntryPostedEvent;
import com.bankledger.ledger.domain.event.JournalPostedEvent;
import com.bankledger.ledger.domain.event.JournalRejectedEvent;
import com.bankledger.ledger.domain.model.EntryDirection;
import com.bankledger.ledger.domain.model.JournalStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain Commands and Events Unit Tests")
class DomainEventsAndCommandsTest {

    private final Instant now = Instant.parse("2026-11-13T14:30:00Z");

    @Test
    @DisplayName("PostJournalCommand records all dispatch fields")
    void postJournalCommandFields() {
        UUID commandId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        PostJournalCommand cmd = new PostJournalCommand(
                commandId,
                transactionId,
                "DEPOSIT",
                10_000L,
                "BRL",
                sourceAccountId,
                destinationAccountId,
                "SYS-CASH-00",
                correlationId,
                now
        );

        assertThat(cmd.commandId()).isEqualTo(commandId);
        assertThat(cmd.transactionId()).isEqualTo(transactionId);
        assertThat(cmd.type()).isEqualTo("DEPOSIT");
        assertThat(cmd.amount()).isEqualTo(10_000L);
        assertThat(cmd.currency()).isEqualTo("BRL");
        assertThat(cmd.sourceAccountId()).isEqualTo(sourceAccountId);
        assertThat(cmd.destinationAccountId()).isEqualTo(destinationAccountId);
        assertThat(cmd.terminalId()).isEqualTo("SYS-CASH-00");
        assertThat(cmd.correlationId()).isEqualTo(correlationId);
        assertThat(cmd.timestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("JournalPostedEvent has canonical topic, event type, and payload")
    void journalPostedEventFields() {
        UUID journalId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        JournalPostedEvent event = new JournalPostedEvent(
                journalId,
                transactionId,
                "DEPOSIT",
                JournalStatus.POSTED,
                10_000L,
                "BRL",
                correlationId,
                now
        );

        assertThat(event.journalId()).isEqualTo(journalId);
        assertThat(event.transactionId()).isEqualTo(transactionId);
        assertThat(event.type()).isEqualTo("DEPOSIT");
        assertThat(event.status()).isEqualTo(JournalStatus.POSTED);
        assertThat(event.totalAmount()).isEqualTo(10_000L);
        assertThat(event.currency()).isEqualTo("BRL");
        assertThat(event.correlationId()).isEqualTo(correlationId);
        assertThat(event.postedAt()).isEqualTo(now);
        assertThat(JournalPostedEvent.TOPIC).isEqualTo("ledger.journal-results.v1");
        assertThat(JournalPostedEvent.EVENT_TYPE).isEqualTo("JournalPosted");
    }

    @Test
    @DisplayName("JournalRejectedEvent has canonical topic, event type, and reject metadata")
    void journalRejectedEventFields() {
        UUID transactionId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        JournalRejectedEvent event = new JournalRejectedEvent(
                transactionId,
                "WITHDRAWAL",
                JournalStatus.REJECTED,
                "INSUFFICIENT_FUNDS",
                "Customer balance is lower than withdrawal amount",
                correlationId,
                now
        );

        assertThat(event.transactionId()).isEqualTo(transactionId);
        assertThat(event.type()).isEqualTo("WITHDRAWAL");
        assertThat(event.status()).isEqualTo(JournalStatus.REJECTED);
        assertThat(event.rejectCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(event.rejectReason()).isEqualTo("Customer balance is lower than withdrawal amount");
        assertThat(event.correlationId()).isEqualTo(correlationId);
        assertThat(event.rejectedAt()).isEqualTo(now);
        assertThat(JournalRejectedEvent.TOPIC).isEqualTo("ledger.journal-results.v1");
        assertThat(JournalRejectedEvent.EVENT_TYPE).isEqualTo("JournalRejected");
    }

    @Test
    @DisplayName("EntryPostedEvent has canonical topic, event type, and audit entry fields")
    void entryPostedEventFields() {
        UUID entryId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        EntryPostedEvent event = new EntryPostedEvent(
                entryId,
                journalId,
                transactionId,
                accountId,
                EntryDirection.CREDIT,
                10_000L,
                10_000L,
                1L,
                now
        );

        assertThat(event.entryId()).isEqualTo(entryId);
        assertThat(event.journalId()).isEqualTo(journalId);
        assertThat(event.transactionId()).isEqualTo(transactionId);
        assertThat(event.accountId()).isEqualTo(accountId);
        assertThat(event.direction()).isEqualTo(EntryDirection.CREDIT);
        assertThat(event.amount()).isEqualTo(10_000L);
        assertThat(event.balanceAfter()).isEqualTo(10_000L);
        assertThat(event.sequence()).isEqualTo(1L);
        assertThat(event.postedAt()).isEqualTo(now);
        assertThat(EntryPostedEvent.TOPIC).isEqualTo("ledger.entries.v1");
        assertThat(EntryPostedEvent.EVENT_TYPE).isEqualTo("EntryPosted");
    }
}
