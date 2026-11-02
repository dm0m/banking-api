package io.github.dm0m.bankingapi.dto;

import java.time.LocalDate;

import io.github.dm0m.bankingapi.validation.Adult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(

    @NotBlank @Size(max = 255) String firstName,
    @NotBlank @Size(max = 255) String lastName,
    @NotBlank @Size(max = 16) String phone,
    @NotBlank @Email @Size(max = 255) String email,
    @NotNull @Valid AddressRequest address,
    @NotNull @Adult LocalDate dob
) {}
