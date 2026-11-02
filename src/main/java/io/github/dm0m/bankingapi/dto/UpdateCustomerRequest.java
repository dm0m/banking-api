package io.github.dm0m.bankingapi.dto;

import io.github.dm0m.bankingapi.validation.NullOrNotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(
    @Size(max = 255)
    @NullOrNotBlank
    String firstName,

    @Size(max = 255)
    @NullOrNotBlank
    String lastName,

    @Size(max = 16)
    @NullOrNotBlank
    String phone,

    @Email
    @Size(max = 255)
    @NullOrNotBlank
    String email,

    @Valid
    AddressRequest address
) {}
