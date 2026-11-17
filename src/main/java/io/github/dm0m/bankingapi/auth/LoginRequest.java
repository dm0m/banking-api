package io.github.dm0m.bankingapi.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Email and password")
public record LoginRequest(
    @Schema(description = "Email address", example = "john.doe@example.com")
    @NotBlank String email,
    @Schema(description = "Password", example = "correct-horse-battery")
    @NotBlank String password
) {}
