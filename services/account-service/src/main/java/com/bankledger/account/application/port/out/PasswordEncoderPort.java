package com.bankledger.account.application.port.out;

/**
 * Outbound port for encoding and verifying passwords.
 * Implemented by security adapters (e.g. BCryptPasswordEncoder).
 */
public interface PasswordEncoderPort {

    String encode(CharSequence rawPassword);

    boolean matches(CharSequence rawPassword, String encodedPassword);
}
