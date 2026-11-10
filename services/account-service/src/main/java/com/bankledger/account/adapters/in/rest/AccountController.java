package com.bankledger.account.adapters.in.rest;

import com.bankledger.account.adapters.in.rest.dto.AccountCreationRequest;
import com.bankledger.account.adapters.in.rest.dto.AccountLookupResponse;
import com.bankledger.account.adapters.in.rest.dto.AccountResponse;
import com.bankledger.account.application.port.in.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final CreateAccountUseCase createAccountUseCase;
    private final GetAccountMeUseCase getAccountMeUseCase;
    private final LookupAccountUseCase lookupAccountUseCase;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountCreationRequest request) {
        CreateAccountCommand command = new CreateAccountCommand(
                request.fullName(),
                request.cpf(),
                request.birthDate(),
                request.email(),
                request.phone(),
                request.password()
        );
        CreatedAccountResult result = createAccountUseCase.createAccount(command);
        URI location = URI.create("/api/v1/accounts/" + result.accountId());
        AccountResponse response = new AccountResponse(
                result.accountId(),
                result.customerId(),
                result.branch(),
                result.accountNumber(),
                result.checkDigit(),
                result.status().name(),
                result.createdAt()
        );
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getAccountMe(
            @AuthenticationPrincipal AuthenticatedUser user,
            Authentication authentication
    ) {
        AuthenticatedUser principal = resolvePrincipal(user, authentication);
        if (principal == null || principal.accountId() == null) {
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
        AccountMeResult result = getAccountMeUseCase.getAccountMe(principal.accountId());
        return ResponseEntity.ok(toAccountResponse(result));
    }

    @GetMapping("/lookup")
    public ResponseEntity<AccountLookupResponse> lookupAccount(
            @RequestParam("agency") String agency,
            @RequestParam("number") String number
    ) {
        AccountLookupResult result = lookupAccountUseCase.lookup(agency, number);
        return ResponseEntity.ok(new AccountLookupResponse(
                result.accountId(),
                result.agency(),
                result.number(),
                result.holderNameMasked()
        ));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<?> getAccountById(
            @PathVariable UUID accountId,
            @AuthenticationPrincipal AuthenticatedUser user,
            Authentication authentication
    ) {
        AuthenticatedUser principal = resolvePrincipal(user, authentication);
        if (principal == null || principal.accountId() == null) {
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

        boolean isOwner = accountId.equals(principal.accountId());
        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(principal.role());
        if (!isOwner && !isAdmin) {
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

        AccountMeResult result = getAccountMeUseCase.getAccountMe(accountId);
        return ResponseEntity.ok(toAccountResponse(result));
    }

    private AuthenticatedUser resolvePrincipal(AuthenticatedUser user, Authentication authentication) {
        if (user != null) {
            return user;
        }
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser authUser) {
            return authUser;
        }
        return null;
    }

    private AccountResponse toAccountResponse(AccountMeResult result) {
        return new AccountResponse(
                result.accountId(),
                result.customerId(),
                result.agency(),
                result.number(),
                result.digit(),
                result.status().name(),
                result.openedAt(),
                result.holderName()
        );
    }
}
