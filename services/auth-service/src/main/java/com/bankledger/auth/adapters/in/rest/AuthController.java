package com.bankledger.auth.adapters.in.rest;

import com.bankledger.auth.adapters.in.rest.dto.IntrospectRequest;
import com.bankledger.auth.adapters.in.rest.dto.IntrospectResponse;
import com.bankledger.auth.adapters.in.rest.dto.LoginRequest;
import com.bankledger.auth.adapters.in.rest.dto.RefreshTokenRequest;
import com.bankledger.auth.adapters.in.rest.dto.TokenResponse;
import com.bankledger.auth.application.port.in.IntrospectCommand;
import com.bankledger.auth.application.port.in.IntrospectTokenUseCase;
import com.bankledger.auth.application.port.in.LoginCommand;
import com.bankledger.auth.application.port.in.LoginUseCase;
import com.bankledger.auth.application.port.in.RefreshTokenCommand;
import com.bankledger.auth.application.port.in.RefreshTokenUseCase;
import com.bankledger.commons.security.RsaJwtSigner;
import com.bankledger.commons.security.SecurityConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final IntrospectTokenUseCase introspectTokenUseCase;
    private final RsaJwtSigner rsaJwtSigner;

    @PostMapping(SecurityConstants.AUTH_LOGIN_PATH)
    public Mono<ResponseEntity<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return loginUseCase.login(new LoginCommand(request.cpf(), request.password()))
                .map(result -> ResponseEntity.ok(new TokenResponse(
                        result.token(),
                        result.expiresIn(),
                        result.tokenType(),
                        result.refreshToken(),
                        new TokenResponse.UserInfo(
                                result.user().id(),
                                result.user().fullName(),
                                result.user().cpf(),
                                result.user().email()
                        )
                )));
    }

    @PostMapping(SecurityConstants.AUTH_REFRESH_PATH)
    public Mono<ResponseEntity<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return refreshTokenUseCase.refreshToken(new RefreshTokenCommand(request.refreshToken()))
                .map(result -> ResponseEntity.ok(new TokenResponse(
                        result.token(),
                        result.expiresIn(),
                        result.tokenType(),
                        result.refreshToken(),
                        new TokenResponse.UserInfo(
                                result.user().id(),
                                result.user().fullName(),
                                result.user().cpf(),
                                result.user().email()
                        )
                )));
    }

    @GetMapping(value = SecurityConstants.JWKS_WELL_KNOWN_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> jwks() {
        return Mono.fromCallable(rsaJwtSigner::toJwksMap)
                .map(ResponseEntity::ok);
    }

    @PostMapping(SecurityConstants.AUTH_INTROSPECT_PATH)
    public Mono<ResponseEntity<IntrospectResponse>> introspect(@Valid @RequestBody IntrospectRequest request) {
        return introspectTokenUseCase.introspect(new IntrospectCommand(request.token()))
                .map(result -> ResponseEntity.ok(new IntrospectResponse(
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
                )));
    }
}
