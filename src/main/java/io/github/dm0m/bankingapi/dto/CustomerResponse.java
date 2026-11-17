package io.github.dm0m.bankingapi.dto;

import java.time.Instant;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Customer")
public record CustomerResponse(
    @Schema(description = "Customer id", example = "1")
    Long id,
    @Schema(description = "First name", example = "John")
    String firstName,
    @Schema(description = "Last name", example = "Doe")
    String lastName,
    @Schema(description = "Phone number", example = "+33612345678")
    String phone,
    @Schema(description = "Email address", example = "john.doe@example.com")
    String email,
    @Schema(description = "Date of birth", example = "1990-05-15")
    LocalDate dob,
    @Schema(description = "Postal address")
    AddressResponse address,
    @Schema(description = "When the customer was created (UTC)", example = "2026-01-15T10:30:00Z")
    Instant createdAt,
    @Schema(description = "Last change (UTC)", example = "2026-01-15T10:30:00Z")
    Instant updatedAt
) {}
