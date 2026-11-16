package com.bankledger.auth.adapters.in.rest;

import com.bankledger.auth.adapters.in.rest.dto.IntrospectRequest;
import com.bankledger.auth.adapters.in.rest.dto.IntrospectResponse;
import com.bankledger.auth.adapters.in.rest.dto.LoginRequest;
import com.bankledger.auth.adapters.in.rest.dto.TokenResponse;
import com.bankledger.auth.application.port.in.IntrospectCommand;
import com.bankledger.auth.application.port.in.IntrospectResult;
import com.bankledger.auth.application.port.in.IntrospectTokenUseCase;
import com.bankledger.auth.application.port.in.LoginCommand;
import com.bankledger.auth.application.port.in.LoginResult;
import com.bankledger.auth.application.port.in.LoginUseCase;
import com.bankledger.commons.security.RsaJwtSigner;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final IntrospectTokenUseCase introspectTokenUseCase;
    private final RsaJwtSigner rsaJwtSigner;

    @PostMapping({"/api/v1/auth/login", "/auth/login"})
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginUseCase.login(new LoginCommand(request.cpf(), request.password()));
        TokenResponse response = new TokenResponse(
                result.token(),
                result.expiresIn(),
                result.tokenType(),
                new TokenResponse.UserInfo(
                        result.user().id(),
                        result.user().fullName(),
                        result.user().cpf(),
                        result.user().email()
                )
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = {"/.well-known/jwks.json", "/api/v1/auth/.well-known/jwks.json"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok(rsaJwtSigner.toJwksMap());
    }

    @PostMapping("/api/v1/auth/introspect")
    public ResponseEntity<IntrospectResponse> introspect(@Valid @RequestBody IntrospectRequest request) {
        IntrospectResult result = introspectTokenUseCase.introspect(new IntrospectCommand(request.token()));
        IntrospectResponse response = new IntrospectResponse(
                result.active(),
                result.sub(),
                result.customerId(),
                result.accountId(),
                result.branch(),
                result.accountNumber(),
                result.role(),
                result.exp(),
                result.iat(),
                result.iss()
        );
        return ResponseEntity.ok(response);
    }
}
