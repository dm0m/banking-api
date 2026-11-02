package io.github.dm0m.bankingapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(

    @NotBlank
    @Size(max = 10)
    String streetNo,

    @NotBlank
    @Size(max = 255)
    String street,

    @NotBlank
    @Size(max = 255)
    String city,

    @NotBlank
    @Size(max = 255)
    String state,

    @NotBlank
    @Size(max = 255)
    String zipCode,

    @NotBlank
    @Size(max = 255)
    String country
) {}
