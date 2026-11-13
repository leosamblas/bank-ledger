package com.bankledger.transaction.adapters.in.rest;

import com.bankledger.transaction.adapters.in.rest.dto.DepositRequest;
import com.bankledger.transaction.adapters.in.rest.dto.TransactionResponse;
import com.bankledger.transaction.adapters.in.rest.dto.WithdrawalRequest;
import com.bankledger.transaction.application.port.in.CreateDepositCommand;
import com.bankledger.transaction.application.port.in.CreateDepositUseCase;
import com.bankledger.transaction.application.port.in.CreateWithdrawalCommand;
import com.bankledger.transaction.application.port.in.CreateWithdrawalUseCase;
import com.bankledger.transaction.application.port.in.GetTransactionUseCase;
import com.bankledger.transaction.application.port.in.IdempotencyCheckResult;
import com.bankledger.transaction.application.port.in.IdempotencyValidatorUseCase;
import com.bankledger.transaction.domain.model.FinancialTransaction;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private static final String ENDPOINT_DEPOSITS = "/api/v1/transactions/deposits";
    private static final String ENDPOINT_WITHDRAWALS = "/api/v1/transactions/withdrawals";

    private final CreateDepositUseCase createDepositUseCase;
    private final CreateWithdrawalUseCase createWithdrawalUseCase;
    private final GetTransactionUseCase getTransactionUseCase;
    private final IdempotencyValidatorUseCase idempotencyValidatorUseCase;
    private final ObjectMapper objectMapper;

    @PostMapping("/deposits")
    public ResponseEntity<?> createDeposit(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody DepositRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if (user == null || user.customerId() == null || user.accountId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String rawBody;
        try {
            rawBody = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize request body", e);
        }
        String requestHash = idempotencyValidatorUseCase.computeHash(rawBody);

        IdempotencyCheckResult checkResult = idempotencyValidatorUseCase.validateOrLock(
                user.customerId(),
                idempotencyKey,
                ENDPOINT_DEPOSITS,
                requestHash
        );

        if (checkResult.isCached()) {
            URI location = URI.create("/api/v1/transactions/" + checkResult.cachedTransactionId());
            return ResponseEntity.status(checkResult.cachedStatus())
                    .location(location)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(checkResult.cachedBody());
        }

        CreateDepositCommand command = new CreateDepositCommand(
                user.accountId(),
                request.amount(),
                request.description(),
                "API",
                user.customerId(),
                UUID.randomUUID()
        );

        FinancialTransaction tx = createDepositUseCase.createDeposit(command);
        TransactionResponse response = TransactionResponse.fromDomain(tx);

        String responseBody;
        try {
            responseBody = objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize TransactionResponse", e);
        }

        idempotencyValidatorUseCase.complete(
                user.customerId(),
                idempotencyKey,
                tx.getId(),
                HttpStatus.ACCEPTED.value(),
                responseBody
        );

        URI location = URI.create("/api/v1/transactions/" + tx.getId());
        return ResponseEntity.accepted()
                .location(location)
                .body(response);
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<?> createWithdrawal(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody WithdrawalRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if (user == null || user.customerId() == null || user.accountId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String rawBody;
        try {
            rawBody = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize request body", e);
        }
        String requestHash = idempotencyValidatorUseCase.computeHash(rawBody);

        IdempotencyCheckResult checkResult = idempotencyValidatorUseCase.validateOrLock(
                user.customerId(),
                idempotencyKey,
                ENDPOINT_WITHDRAWALS,
                requestHash
        );

        if (checkResult.isCached()) {
            URI location = URI.create("/api/v1/transactions/" + checkResult.cachedTransactionId());
            return ResponseEntity.status(checkResult.cachedStatus())
                    .location(location)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(checkResult.cachedBody());
        }

        CreateWithdrawalCommand command = new CreateWithdrawalCommand(
                user.accountId(),
                request.amount(),
                request.description(),
                "API",
                user.customerId(),
                UUID.randomUUID()
        );

        FinancialTransaction tx = createWithdrawalUseCase.createWithdrawal(command);
        TransactionResponse response = TransactionResponse.fromDomain(tx);

        String responseBody;
        try {
            responseBody = objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize TransactionResponse", e);
        }

        idempotencyValidatorUseCase.complete(
                user.customerId(),
                idempotencyKey,
                tx.getId(),
                HttpStatus.ACCEPTED.value(),
                responseBody
        );

        URI location = URI.create("/api/v1/transactions/" + tx.getId());
        return ResponseEntity.accepted()
                .location(location)
                .body(response);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID transactionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if (user == null || user.customerId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        FinancialTransaction tx = getTransactionUseCase.getTransaction(transactionId, user.customerId());
        return ResponseEntity.ok(TransactionResponse.fromDomain(tx));
    }
}
