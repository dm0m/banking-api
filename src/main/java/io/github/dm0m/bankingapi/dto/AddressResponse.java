package io.github.dm0m.bankingapi.dto;

public record AddressResponse(
    String streetNo,
    String street,
    String city,
    String state,
    String zipCode,
    String country
) {}
