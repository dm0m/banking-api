package io.github.dm0m.bankingapi.dto;

import java.time.Instant;
import java.time.LocalDate;

public record CustomerResponse(
    Long id,
    String firstName,
    String lastName,
    String phone,
    String email,
    LocalDate dob,
    AddressResponse address,
    Instant createdAt,
    Instant updatedAt
) {}
