package io.github.dm0m.bankingapi.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Activate a customer's login with the code issued by an admin")
public record RegisterRequest(
    @Schema(description = "The customer's email address", example = "john.doe@example.com")
    @NotBlank @Email @Size(max = 255) String email,
    @Schema(description = "One-time activation code issued by an admin", example = "q3Zt8vXk2LmN9pRw4sYb1A")
    @NotBlank @Size(max = 64) String activationCode,
    @Schema(description = "New password, 12 to 72 characters", example = "correct-horse-battery")
    @NotBlank @Size(min = 12, max = 72) String password
) {}
