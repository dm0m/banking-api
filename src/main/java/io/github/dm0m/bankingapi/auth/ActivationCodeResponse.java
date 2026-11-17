package io.github.dm0m.bankingapi.auth;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One-time activation code; shown only once, only its hash is stored")
public record ActivationCodeResponse(
    @Schema(description = "Code to hand to the customer", example = "q3Zt8vXk2LmN9pRw4sYb1A")
    String activationCode,
    @Schema(description = "When the code stops working (UTC)", example = "2026-01-18T10:30:00Z")
    Instant expiresAt
) {}
