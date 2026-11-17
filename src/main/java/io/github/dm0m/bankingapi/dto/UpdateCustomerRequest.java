package io.github.dm0m.bankingapi.dto;

import io.github.dm0m.bankingapi.validation.NullOrNotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Schema(description = "Partial customer update: omitted or null fields are left unchanged")
public record UpdateCustomerRequest(
    @Size(max = 255)
    @NullOrNotBlank
    @Schema(description = "First name", example = "John")
    String firstName,

    @Size(max = 255)
    @NullOrNotBlank
    @Schema(description = "Last name", example = "Doe")
    String lastName,

    @Size(max = 16)
    @NullOrNotBlank
    @Schema(description = "Phone number, international format", example = "+33612345678")
    String phone,

    @Email
    @Size(max = 255)
    @NullOrNotBlank
    @Schema(description = "Email address, unique across customers", example = "john.doe@example.com")
    String email,

    @Valid
    @Schema(description = "Replaces the whole address when present")
    AddressRequest address
) {}
