package com.bankledger.auth.adapters.out.security;

import com.bankledger.auth.application.port.out.PasswordEncoderPort;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Primary password encoder adapter using standard BCrypt with strength 10 for
 * secure, high-performance hashing under concurrency.
 */
@Component
@Primary
public class BCryptPasswordEncoderAdapter implements PasswordEncoderPort, PasswordEncoder {

    public static final int BCRYPT_STRENGTH = 10;

    private final BCryptPasswordEncoder bcryptDelegate;

    public BCryptPasswordEncoderAdapter() {
        this(new BCryptPasswordEncoder(BCRYPT_STRENGTH));
    }

    public BCryptPasswordEncoderAdapter(BCryptPasswordEncoder bcryptDelegate) {
        this.bcryptDelegate = bcryptDelegate != null ? bcryptDelegate : new BCryptPasswordEncoder(BCRYPT_STRENGTH);
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
        return bcryptDelegate.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        return bcryptDelegate.upgradeEncoding(encodedPassword);
    }
}
