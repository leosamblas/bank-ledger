package com.bankledger.transaction.domain;

import com.bankledger.commons.events.v1.ledger.JournalPostedPayload;
import com.bankledger.commons.events.v1.ledger.JournalRejectedPayload;
import com.bankledger.commons.events.v1.ledger.PostJournalCommandPayload;
import com.bankledger.transaction.domain.command.PostJournalCommand;
import com.bankledger.transaction.domain.event.JournalResultPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostJournalCommand and JournalResultPayload Alignment Tests")
class PostJournalCommandAndPayloadTest {

    @Test
    @DisplayName("Should convert between PostJournalCommand and canonical PostJournalCommandPayload")
    void shouldConvertCommandToAndFromPayload() {
        UUID commandId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        UUID srcId = UUID.randomUUID();
        UUID dstId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant now = Instant.now();

        PostJournalCommand command = new PostJournalCommand(
                commandId,
                txId,
                "TRANSFER",
                7500L,
                "BRL",
                srcId,
                dstId,
                "TERM-01",
                correlationId,
                now
        );

        PostJournalCommandPayload payload = command.toPayload();
        assertThat(payload.commandId()).isEqualTo(commandId);
        assertThat(payload.transactionId()).isEqualTo(txId);
        assertThat(payload.type()).isEqualTo("TRANSFER");
        assertThat(payload.amount()).isEqualTo(7500L);
        assertThat(payload.currency()).isEqualTo("BRL");
        assertThat(payload.sourceAccountId()).isEqualTo(srcId);
        assertThat(payload.destinationAccountId()).isEqualTo(dstId);
        assertThat(payload.terminalId()).isEqualTo("TERM-01");
        assertThat(payload.correlationId()).isEqualTo(correlationId);
        assertThat(payload.timestamp()).isEqualTo(now);

        PostJournalCommand reconstructed = PostJournalCommand.fromPayload(payload);
        assertThat(reconstructed).isEqualTo(command);
    }

    @Test
    @DisplayName("Should create JournalResultPayload from canonical JournalPostedPayload")
    void shouldCreateResultFromJournalPostedPayload() {
        UUID journalId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant postedAt = Instant.now();

        JournalPostedPayload postedPayload = new JournalPostedPayload(
                journalId,
                txId,
                "DEPOSIT",
                "POSTED",
                10000L,
                "BRL",
                correlationId,
                postedAt
        );

        JournalResultPayload result = JournalResultPayload.fromPosted(postedPayload);
        assertThat(result.journalId()).isEqualTo(journalId);
        assertThat(result.transactionId()).isEqualTo(txId);
        assertThat(result.type()).isEqualTo("DEPOSIT");
        assertThat(result.status()).isEqualTo("POSTED");
        assertThat(result.totalAmount()).isEqualTo(10000L);
        assertThat(result.currency()).isEqualTo("BRL");
        assertThat(result.rejectCode()).isNull();
        assertThat(result.rejectReason()).isNull();
        assertThat(result.correlationId()).isEqualTo(correlationId);
        assertThat(result.timestamp()).isEqualTo(postedAt);
    }

    @Test
    @DisplayName("Should create JournalResultPayload from canonical JournalRejectedPayload")
    void shouldCreateResultFromJournalRejectedPayload() {
        UUID txId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant rejectedAt = Instant.now();

        JournalRejectedPayload rejectedPayload = new JournalRejectedPayload(
                txId,
                "WITHDRAWAL",
                "REJECTED",
                "INSUFFICIENT_FUNDS",
                "Insufficient balance",
                correlationId,
                rejectedAt
        );

        JournalResultPayload result = JournalResultPayload.fromRejected(rejectedPayload);
        assertThat(result.journalId()).isNull();
        assertThat(result.transactionId()).isEqualTo(txId);
        assertThat(result.type()).isEqualTo("WITHDRAWAL");
        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.totalAmount()).isNull();
        assertThat(result.currency()).isNull();
        assertThat(result.rejectCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(result.rejectReason()).isEqualTo("Insufficient balance");
        assertThat(result.correlationId()).isEqualTo(correlationId);
        assertThat(result.timestamp()).isEqualTo(rejectedAt);
    }
}
