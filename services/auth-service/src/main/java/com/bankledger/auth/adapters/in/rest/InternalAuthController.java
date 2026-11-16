package com.bankledger.auth.adapters.in.rest;

import com.bankledger.auth.adapters.in.rest.dto.RegisterCredentialsRequest;
import com.bankledger.auth.application.port.in.RegisterCredentialsCommand;
import com.bankledger.auth.application.port.in.RegisterCredentialsUseCase;
import com.bankledger.auth.domain.model.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final RegisterCredentialsUseCase registerCredentialsUseCase;

    public record CredentialResponse(UUID id, UUID customerId, String cpf) {}

    @PostMapping("/credentials")
    public ResponseEntity<CredentialResponse> registerCredentials(@Valid @RequestBody RegisterCredentialsRequest request) {
        AuthUser user = registerCredentialsUseCase.registerCredentials(new RegisterCredentialsCommand(
                request.customerId(),
                request.cpf(),
                request.password(),
                request.accountId(),
                request.branch(),
                request.accountNumber(),
                request.fullName(),
                request.email()
        ));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CredentialResponse(user.getId(), user.getCustomerId(), user.getCpf()));
    }
}
