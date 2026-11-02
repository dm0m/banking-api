package io.github.dm0m.bankingapi.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.dm0m.bankingapi.dto.AddressRequest;
import io.github.dm0m.bankingapi.dto.AddressResponse;
import io.github.dm0m.bankingapi.model.Address;

class AddressMapperTest {

    private final AddressMapper mapper = new AddressMapper();

    private final AddressRequest request =
            new AddressRequest("12", "Rue de Rivoli", "Paris", "Ile-de-France", "75001", "France");

    @Test
    void toEntity_copiesAllFields() {
        Address address = mapper.toEntity(request);

        assertThat(address.getStreetNo()).isEqualTo("12");
        assertThat(address.getStreet()).isEqualTo("Rue de Rivoli");
        assertThat(address.getCity()).isEqualTo("Paris");
        assertThat(address.getState()).isEqualTo("Ile-de-France");
        assertThat(address.getZipCode()).isEqualTo("75001");
        assertThat(address.getCountry()).isEqualTo("France");
    }

    @Test
    void updateEntity_overwritesFieldsOfExistingAddress() {
        Address existing = mapper.toEntity(
                new AddressRequest("5", "Cours Mirabeau", "Aix-en-Provence", "Provence", "13100", "France"));

        mapper.updateEntity(existing, request);

        assertThat(existing.getStreetNo()).isEqualTo("12");
        assertThat(existing.getStreet()).isEqualTo("Rue de Rivoli");
        assertThat(existing.getCity()).isEqualTo("Paris");
        assertThat(existing.getState()).isEqualTo("Ile-de-France");
        assertThat(existing.getZipCode()).isEqualTo("75001");
        assertThat(existing.getCountry()).isEqualTo("France");
    }

    @Test
    void toResponse_copiesAllFields() {
        Address address = mapper.toEntity(request);

        AddressResponse response = mapper.toResponse(address);

        assertThat(response).isEqualTo(
                new AddressResponse("12", "Rue de Rivoli", "Paris", "Ile-de-France", "75001", "France"));
    }
}
