package com.bankledger.ledger.application.service;

import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.ledger.application.port.in.ProvisionAccountUseCase;
import com.bankledger.ledger.application.port.out.LedgerAccountRepositoryPort;
import com.bankledger.ledger.application.port.out.OutboxPort;
import com.bankledger.ledger.domain.event.AccountOpenedPayload;
import com.bankledger.ledger.domain.event.LedgerAccountProvisionedEvent;
import com.bankledger.ledger.domain.model.AccountKind;
import com.bankledger.ledger.domain.model.AccountStatus;
import com.bankledger.ledger.domain.model.LedgerAccount;
import com.bankledger.ledger.domain.model.NormalBalance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for provisioning ledger accounts upon receiving AccountOpened events.
 * Enforces idempotency, creates the double-entry customer ledger account, and records
 * a LedgerAccountProvisioned event in the transactional outbox.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProvisionAccountService implements ProvisionAccountUseCase {

    private final LedgerAccountRepositoryPort ledgerAccountRepositoryPort;
    private final OutboxPort outboxPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public void provisionAccount(AccountOpenedPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        UUID accountId = Objects.requireNonNull(payload.accountId(), "payload.accountId must not be null");

        Optional<LedgerAccount> existing = ledgerAccountRepositoryPort.findById(accountId);
        if (existing.isPresent()) {
            log.info("Ledger account already exists for accountId={}, skipping provisioning (idempotent)", accountId);
            return;
        }

        String code = "ACC-" + payload.branch() + "-" + payload.accountNumber() + "-" + payload.checkDigit();
        Instant now = Instant.now(clock);
        Instant openedAt = payload.openedAt() != null ? payload.openedAt() : now;

        LedgerAccount account = LedgerAccount.builder()
                .id(accountId)
                .code(code)
                .kind(AccountKind.CUSTOMER)
                .normalBalance(NormalBalance.CREDIT)
                .currency("BRL")
                .balance(0L)
                .lastSequence(0L)
                .status(AccountStatus.ACTIVE)
                .createdAt(openedAt)
                .updatedAt(openedAt)
                .version(0L)
                .build();

        ledgerAccountRepositoryPort.save(account);

        LedgerAccountProvisionedEvent event = new LedgerAccountProvisionedEvent(
                account.getId(),
                account.getCode(),
                now
        );

        OutboxEvent outboxEvent = OutboxEvent.pending(
                "LedgerAccount",
                account.getId(),
                LedgerAccountProvisionedEvent.EVENT_TYPE,
                LedgerAccountProvisionedEvent.TOPIC,
                account.getId().toString(),
                toJson(event)
        );

        outboxPort.save(outboxEvent);

        log.info("Successfully provisioned ledger account id={} code={}", account.getId(), account.getCode());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize " + value.getClass().getSimpleName() + " to JSON", e);
        }
    }
}
