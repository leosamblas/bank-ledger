package com.bankledger.query.adapters.in.rest;

import com.bankledger.query.adapters.in.rest.dto.BalanceResponse;
import com.bankledger.query.adapters.in.rest.dto.StatementItemResponse;
import com.bankledger.query.adapters.in.rest.dto.StatementResponse;
import com.bankledger.query.adapters.out.persistence.AccountBalanceEntity;
import com.bankledger.query.adapters.out.persistence.AccountBalanceRepository;
import com.bankledger.query.adapters.out.persistence.StatementEntryEntity;
import com.bankledger.query.adapters.out.persistence.StatementEntryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller serving CQRS queries for account balances and historical statement entries.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/queries")
@RequiredArgsConstructor
@Tag(name = "Query Service", description = "CQRS Read projections for account balances and bank statements")
public class QueryController {

    private final AccountBalanceRepository accountBalanceRepository;
    private final StatementEntryRepository statementEntryRepository;

    @Operation(summary = "Get account balance", description = "Returns available balance, limits, and projection sequence")
    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID accountId) {
        log.info("Querying balance for accountId={}", accountId);

        BalanceResponse response = accountBalanceRepository.findById(accountId)
                .map(entity -> BalanceResponse.of(
                        entity.getAccountId(),
                        entity.getBalance(),
                        entity.getCurrency(),
                        entity.getLastSequence(),
                        entity.getUpdatedAt()
                ))
                .orElseGet(() -> {
                    log.info("No balance found for accountId={}, initializing and returning zero balance", accountId);
                    AccountBalanceEntity zeroEntity = AccountBalanceEntity.builder()
                            .accountId(accountId)
                            .balance(0L)
                            .currency("BRL")
                            .lastSequence(0L)
                            .updatedAt(Instant.now())
                            .build();
                    try {
                        accountBalanceRepository.save(zeroEntity);
                    } catch (Exception ex) {
                        log.debug("Concurrent insertion when initializing zero balance: {}", ex.getMessage());
                    }
                    return BalanceResponse.zero(accountId);
                });

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get account statement", description = "Returns ordered list of statement entries for the given account")
    @GetMapping("/accounts/{accountId}/statement")
    public ResponseEntity<StatementResponse> getStatement(
            @PathVariable UUID accountId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false, defaultValue = "50") int size
    ) {
        log.info("Querying statement for accountId={}, from={}, to={}, size={}", accountId, from, to, size);

        List<StatementEntryEntity> entities;
        if (from != null && to != null) {
            entities = statementEntryRepository.findByAccountIdAndPostedAtBetweenOrderBySequenceDesc(accountId, from, to);
        } else {
            entities = statementEntryRepository.findByAccountIdOrderBySequenceDesc(accountId);
        }

        List<StatementItemResponse> items = entities.stream()
                .limit(Math.max(1, Math.min(size, 500)))
                .map(this::toItemResponse)
                .toList();

        return ResponseEntity.ok(new StatementResponse(items));
    }

    private StatementItemResponse toItemResponse(StatementEntryEntity entity) {
        return new StatementItemResponse(
                entity.getId(),
                entity.getSequence(),
                entity.getTransactionId(),
                entity.getJournalId(),
                entity.getPostedAt(),
                entity.getPostedAt(),
                entity.getCategory(),
                entity.getType(),
                entity.getDescription(),
                entity.getCounterpartyName(),
                entity.getCounterpartyDocumentMasked(),
                entity.getCounterpartyAccount(),
                entity.getDirection(),
                entity.getDirection(),
                entity.getAmount(),
                entity.getAmount(),
                entity.getBalanceAfter(),
                entity.getBalanceAfter(),
                entity.getIdempotencyKey(),
                entity.getFailureReason()
        );
    }
}
