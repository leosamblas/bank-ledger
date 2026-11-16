package com.bankledger.auth.adapters.in.rest;

import com.bankledger.auth.adapters.in.rest.dto.RegisterCredentialsRequest;
import com.bankledger.auth.application.port.in.RegisterCredentialsCommand;
import com.bankledger.auth.application.port.in.RegisterCredentialsUseCase;
import com.bankledger.commons.security.SecurityConstants;
import com.bankledger.commons.security.dto.RegisterCredentialsPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class InternalAuthController {

    private final RegisterCredentialsUseCase registerCredentialsUseCase;

    public record CredentialResponse(UUID id, UUID customerId, String cpf) {}

    @PostMapping(SecurityConstants.INTERNAL_AUTH_CREDENTIALS_PATH)
    public Mono<ResponseEntity<CredentialResponse>> registerCredentials(@Valid @RequestBody RegisterCredentialsRequest request) {
        log.info("Registering internal credentials for customerId: {}", request.customerId());
        RegisterCredentialsPayload payload = request.toPayload();
        return Mono.fromCallable(() -> registerCredentialsUseCase.registerCredentials(RegisterCredentialsCommand.fromPayload(payload)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(user -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new CredentialResponse(user.getId(), user.getCustomerId(), user.getCpf())));
    }
}
