package com.bankledger.query.adapters.in.rest;

import com.bankledger.commons.security.CommonAuthenticatedUser;
import com.bankledger.commons.security.SecurityConstants;
import com.bankledger.query.adapters.in.rest.dto.BalanceResponse;
import com.bankledger.query.adapters.in.rest.dto.StatementItemResponse;
import com.bankledger.query.adapters.in.rest.dto.StatementResponse;
import com.bankledger.query.adapters.out.persistence.AccountBalanceEntity;
import com.bankledger.query.adapters.out.persistence.AccountBalanceRepository;
import com.bankledger.query.adapters.out.persistence.StatementEntryEntity;
import com.bankledger.query.adapters.out.persistence.StatementEntryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * REST controller serving CQRS queries for account balances and historical statement entries.
 * Hardened against OWASP BOLA / IDOR by enforcing account ownership verification.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/queries")
@Tag(name = "Query Service", description = "CQRS Read projections for account balances and bank statements")
public class QueryController {

    private static final ZoneId ZONE_SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final long DEFAULT_DAILY_LIMIT_CENTS = 5_000_000L;

    private final AccountBalanceRepository accountBalanceRepository;
    private final StatementEntryRepository statementEntryRepository;
    private final Clock clock;

    @Autowired
    public QueryController(
            AccountBalanceRepository accountBalanceRepository,
            StatementEntryRepository statementEntryRepository,
            Clock clock
    ) {
        this.accountBalanceRepository = accountBalanceRepository;
        this.statementEntryRepository = statementEntryRepository;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public QueryController(
            AccountBalanceRepository accountBalanceRepository,
            StatementEntryRepository statementEntryRepository
    ) {
        this(accountBalanceRepository, statementEntryRepository, Clock.systemUTC());
    }

    @Operation(summary = "Get account balance", description = "Returns available balance, limits, and projection sequence")
    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<?> getBalance(
            @PathVariable UUID accountId,
            @AuthenticationPrincipal CommonAuthenticatedUser user,
            Authentication authentication
    ) {
        CommonAuthenticatedUser principal = resolvePrincipal(user, authentication);
        ResponseEntity<?> authCheck = checkOwnership(accountId, principal);
        if (authCheck != null) {
            return authCheck;
        }

        log.info("Querying balance for accountId={}", accountId);

        Instant now = clock.instant();
        Instant startOfDay = now.atZone(ZONE_SAO_PAULO)
                .toLocalDate()
                .atStartOfDay(ZONE_SAO_PAULO)
                .toInstant();

        Long usedToday = statementEntryRepository
                .sumAmountByAccountIdAndDirectionAndCategoryAndPostedAtGreaterThanEqual(
                        accountId, "DEBIT", "TRANSFER", startOfDay);
        long usedTodayCents = usedToday != null ? usedToday : 0L;

        BalanceResponse response = accountBalanceRepository.findById(accountId)
                .map(entity -> {
                    long limit = entity.getDailyTransferLimitCents() > 0
                            ? entity.getDailyTransferLimitCents()
                            : DEFAULT_DAILY_LIMIT_CENTS;
                    return BalanceResponse.of(
                            entity.getAccountId(),
                            entity.getBalance(),
                            entity.getCurrency(),
                            entity.getLastSequence(),
                            entity.getUpdatedAt(),
                            limit,
                            usedTodayCents
                    );
                })
                .orElseGet(() -> {
                    log.info("No balance found for accountId={}, initializing and returning zero balance", accountId);
                    AccountBalanceEntity zeroEntity = AccountBalanceEntity.builder()
                            .accountId(accountId)
                            .balance(0L)
                            .currency("BRL")
                            .lastSequence(0L)
                            .dailyTransferLimitCents(DEFAULT_DAILY_LIMIT_CENTS)
                            .updatedAt(now)
                            .build();
                    try {
                        accountBalanceRepository.save(zeroEntity);
                    } catch (Exception ex) {
                        log.debug("Concurrent insertion when initializing zero balance: {}", ex.getMessage());
                    }
                    return BalanceResponse.of(
                            accountId,
                            0L,
                            "BRL",
                            0L,
                            now,
                            DEFAULT_DAILY_LIMIT_CENTS,
                            usedTodayCents
                    );
                });

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get account statement", description = "Returns ordered list of statement entries for the given account")
    @GetMapping("/accounts/{accountId}/statement")
    public ResponseEntity<?> getStatement(
            @PathVariable UUID accountId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false, defaultValue = "50") int size,
            @AuthenticationPrincipal CommonAuthenticatedUser user,
            Authentication authentication
    ) {
        CommonAuthenticatedUser principal = resolvePrincipal(user, authentication);
        ResponseEntity<?> authCheck = checkOwnership(accountId, principal);
        if (authCheck != null) {
            return authCheck;
        }

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

    private CommonAuthenticatedUser resolvePrincipal(CommonAuthenticatedUser user, Authentication authentication) {
        if (user != null) {
            return user;
        }
        if (authentication != null && authentication.getPrincipal() instanceof CommonAuthenticatedUser authUser) {
            return authUser;
        }
        return null;
    }

    private ResponseEntity<?> checkOwnership(UUID accountId, CommonAuthenticatedUser user) {
        if (user == null) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required to access this resource"
            );
            problem.setTitle("Unauthorized");
            problem.setType(URI.create("about:blank"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }

        boolean isAdmin = SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(user.getRole())
                || "ADMIN".equalsIgnoreCase(user.getRole())
                || (user.getAuthorities() != null && user.getAuthorities().stream()
                        .anyMatch(a -> SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(a.getAuthority())
                                || "ADMIN".equalsIgnoreCase(a.getAuthority())));

        if (isAdmin) {
            return null;
        }

        if (user.getAccountId() == null) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication token missing required accountId claim"
            );
            problem.setTitle("Unauthorized");
            problem.setType(URI.create("about:blank"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }

        boolean isOwner = accountId.equals(user.getAccountId());
        if (!isOwner) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.FORBIDDEN,
                    "Access denied: user is not the owner of this account"
            );
            problem.setTitle("Forbidden");
            problem.setType(URI.create("about:blank"));
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }

        return null;
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
                entity.getFailureReason(),
                entity.getProtocolNumber(),
                entity.getCounterpartyDocument()
        );
    }
}
