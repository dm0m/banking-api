package io.github.dm0m.bankingapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access token to send as 'Authorization: Bearer <accessToken>'")
public record TokenResponse(
    @Schema(description = "Signed JWT", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl")
    String accessToken,
    @Schema(description = "Always Bearer", example = "Bearer")
    String tokenType,
    @Schema(description = "Seconds until the token expires", example = "900")
    long expiresIn
) {}
