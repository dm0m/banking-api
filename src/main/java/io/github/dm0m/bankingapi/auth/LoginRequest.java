package io.github.dm0m.bankingapi.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Email and password")
public record LoginRequest(
    @Schema(description = "Email address", example = "john.doe@example.com")
    @NotBlank @Size(max = 255) String email,
    @Schema(description = "Password", example = "correct-horse-battery")
    @NotBlank String password
) {}
