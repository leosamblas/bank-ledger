package com.bankledger.auth.adapters.out.security;

import com.bankledger.auth.application.port.out.PasswordEncoderPort;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Primary password encoder adapter using BCrypt with strength 10 for high-performance
 * hashing under concurrency, with backward compatibility fallback for Argon2id hashes.
 */
@Component
@Primary
public class BCryptPasswordEncoderAdapter implements PasswordEncoderPort, PasswordEncoder {

    public static final int BCRYPT_STRENGTH = 10;

    private final BCryptPasswordEncoder bcryptDelegate;
    private final PasswordEncoder legacyArgon2Delegate;

    public BCryptPasswordEncoderAdapter() {
        this(new BCryptPasswordEncoder(BCRYPT_STRENGTH), new Argon2PasswordEncoder(16, 32, 1, 19456, 3));
    }

    public BCryptPasswordEncoderAdapter(BCryptPasswordEncoder bcryptDelegate, PasswordEncoder legacyArgon2Delegate) {
        this.bcryptDelegate = bcryptDelegate;
        this.legacyArgon2Delegate = legacyArgon2Delegate;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return bcryptDelegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        if (encodedPassword.startsWith("$argon2")) {
            return legacyArgon2Delegate.matches(rawPassword, encodedPassword);
        }
        return bcryptDelegate.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        if (encodedPassword.startsWith("$argon2")) {
            return true;
        }
        return bcryptDelegate.upgradeEncoding(encodedPassword);
    }
}
