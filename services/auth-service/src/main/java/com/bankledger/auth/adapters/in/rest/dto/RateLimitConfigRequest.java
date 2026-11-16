package com.bankledger.auth.adapters.in.rest.dto;

import jakarta.validation.constraints.Min;

public record RateLimitConfigRequest(
        @Min(value = 1, message = "TPS must be at least 1")
        int tps
) {}
