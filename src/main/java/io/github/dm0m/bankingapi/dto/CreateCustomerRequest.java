package io.github.dm0m.bankingapi.dto;

import java.time.LocalDate;

import io.github.dm0m.bankingapi.validation.Adult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "New customer")
public record CreateCustomerRequest(

    @Schema(description = "First name", example = "John")
    @NotBlank @Size(max = 255) String firstName,
    @Schema(description = "Last name", example = "Doe")
    @NotBlank @Size(max = 255) String lastName,
    @Schema(description = "Phone number, international format", example = "+33612345678")
    @NotBlank @Size(max = 16) String phone,
    @Schema(description = "Email address, unique across customers", example = "john.doe@example.com")
    @NotBlank @Email @Size(max = 255) String email,
    @Schema(description = "Postal address")
    @NotNull @Valid AddressRequest address,
    @Schema(description = "Date of birth; the customer must be an adult", example = "1990-05-15")
    @NotNull @Adult LocalDate dob
) {}
