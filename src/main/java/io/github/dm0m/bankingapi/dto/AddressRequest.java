package io.github.dm0m.bankingapi.dto;

import io.github.dm0m.bankingapi.validation.CountryCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Postal address")
public record AddressRequest(

    @NotBlank
    @Size(max = 10)
    @Schema(description = "Street number", example = "12")
    String streetNo,

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Street name", example = "Rue de Rivoli")
    String street,

    @NotBlank
    @Size(max = 255)
    @Schema(description = "City", example = "Paris")
    String city,

    @NotBlank
    @Size(max = 255)
    @Schema(description = "State or region", example = "Ile-de-France")
    String state,

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Postal code", example = "75001")
    String zipCode,

    @NotBlank
    @CountryCode
    @Schema(description = "ISO 3166-1 alpha-2 country code. Opening an account also requires an IBAN-supported country: AT, BE, DE, ES, FI, FR or PT", example = "FR")
    String country
) {}
