package com.bankledger.commons.constant;

/**
 * Standard HTTP header names and prefixes used in banking REST endpoints.
 */
public final class HttpHeaderConstants {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String LOCATION = "Location";
    public static final String CONTENT_TYPE = "Content-Type";

    private HttpHeaderConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
