package io.github.dm0m.bankingapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;


@Schema(description = "Postal address")
public record AddressResponse(
    @Schema(description = "Street number", example = "12")
    String streetNo,
    @Schema(description = "Street name", example = "Rue de Rivoli")
    String street,
    @Schema(description = "City", example = "Paris")
    String city,
    @Schema(description = "State or region", example = "Ile-de-France")
    String state,
    @Schema(description = "Postal code", example = "75001")
    String zipCode,
    @Schema(description = "ISO 3166-1 alpha-2 country code", example = "FR")
    String country
) {}
