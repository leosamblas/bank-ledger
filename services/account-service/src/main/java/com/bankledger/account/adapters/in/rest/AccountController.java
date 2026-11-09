package com.bankledger.account.adapters.in.rest;

import com.bankledger.account.adapters.in.rest.dto.AccountCreationRequest;
import com.bankledger.account.adapters.in.rest.dto.AccountLookupResponse;
import com.bankledger.account.adapters.in.rest.dto.AccountResponse;
import com.bankledger.account.application.port.in.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final CreateAccountUseCase createAccountUseCase;
    private final GetAccountMeUseCase getAccountMeUseCase;
    private final LookupAccountUseCase lookupAccountUseCase;

    public AccountController(
            CreateAccountUseCase createAccountUseCase,
            GetAccountMeUseCase getAccountMeUseCase,
            LookupAccountUseCase lookupAccountUseCase
    ) {
        this.createAccountUseCase = createAccountUseCase;
        this.getAccountMeUseCase = getAccountMeUseCase;
        this.lookupAccountUseCase = lookupAccountUseCase;
    }

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
    public ResponseEntity<AccountResponse> getAccountMe(
            @AuthenticationPrincipal AuthenticatedUser user,
            org.springframework.security.core.Authentication authentication
    ) {
        AuthenticatedUser principal = user;
        if (principal == null && authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser authUser) {
            principal = authUser;
        }
        if (principal == null || principal.accountId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
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
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable UUID accountId) {
        AccountMeResult result = getAccountMeUseCase.getAccountMe(accountId);
        return ResponseEntity.ok(toAccountResponse(result));
    }

    private AccountResponse toAccountResponse(AccountMeResult result) {
        return new AccountResponse(
                result.accountId(),
                result.customerId(),
                result.agency(),
                result.number(),
                result.digit(),
                result.status().name(),
                result.openedAt()
        );
    }
}
