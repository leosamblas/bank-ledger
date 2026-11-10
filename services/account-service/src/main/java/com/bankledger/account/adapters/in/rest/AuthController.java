package com.bankledger.account.adapters.in.rest;

import com.bankledger.account.adapters.in.rest.dto.LoginRequest;
import com.bankledger.account.adapters.in.rest.dto.TokenResponse;
import com.bankledger.account.application.port.in.LoginCommand;
import com.bankledger.account.application.port.in.LoginResult;
import com.bankledger.account.application.port.in.LoginUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginCommand command = new LoginCommand(request.cpf(), request.password());
        LoginResult result = loginUseCase.login(command);
        return ResponseEntity.ok(new TokenResponse(result.token(), result.expiresIn()));
    }
}
