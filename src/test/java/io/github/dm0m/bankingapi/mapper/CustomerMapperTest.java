package io.github.dm0m.bankingapi.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.dm0m.bankingapi.dto.AddressRequest;
import io.github.dm0m.bankingapi.dto.AddressResponse;
import io.github.dm0m.bankingapi.dto.CreateCustomerRequest;
import io.github.dm0m.bankingapi.dto.CustomerResponse;
import io.github.dm0m.bankingapi.model.Customer;

class CustomerMapperTest {

    // real AddressMapper rather than a mock: both are plain mapping code with no dependencies
    private final CustomerMapper mapper = new CustomerMapper(new AddressMapper());

    private final AddressRequest address =
            new AddressRequest("12", "Rue de Rivoli", "Paris", "Ile-de-France", "75001", "France");

    private CreateCustomerRequest request(String email) {
        return new CreateCustomerRequest("John", "Doe", "+33612345678", email, address, LocalDate.of(1990, 5, 15));
    }

    @Test
    void toEntity_copiesAllFields() {
        Customer customer = mapper.toEntity(request("john@example.com"));

        assertThat(customer.getFirstName()).isEqualTo("John");
        assertThat(customer.getLastName()).isEqualTo("Doe");
        assertThat(customer.getPhone()).isEqualTo("+33612345678");
        assertThat(customer.getEmail()).isEqualTo("john@example.com");
        assertThat(customer.getDob()).isEqualTo(LocalDate.of(1990, 5, 15));
    }

    @Test
    void toEntity_mapsNestedAddress() {
        Customer customer = mapper.toEntity(request("john@example.com"));

        assertThat(customer.getAddress()).isNotNull();
        assertThat(customer.getAddress().getCity()).isEqualTo("Paris");
        assertThat(customer.getAddress().getZipCode()).isEqualTo("75001");
    }

    @Test
    void toEntity_leavesIdUnset() {
        Customer customer = mapper.toEntity(request("john@example.com"));

        assertThat(customer.getId()).isNull();
    }

    @Test
    void toEntity_normalizesEmail() {
        Customer customer = mapper.toEntity(request("  John.Doe@Example.COM "));

        assertThat(customer.getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    void toResponse_copiesAllFields() {
        Customer customer = mapper.toEntity(request("john@example.com"));
        // id and audit dates have no setters (the database and auditing fill them), so set them directly
        Instant created = Instant.parse("2026-11-01T10:00:00Z");
        Instant updated = Instant.parse("2026-11-02T10:00:00Z");
        ReflectionTestUtils.setField(customer, "id", 42L);
        ReflectionTestUtils.setField(customer, "createdAt", created);
        ReflectionTestUtils.setField(customer, "updatedAt", updated);

        CustomerResponse response = mapper.toResponse(customer);

        assertThat(response).isEqualTo(new CustomerResponse(
                42L, "John", "Doe", "+33612345678", "john@example.com", LocalDate.of(1990, 5, 15),
                new AddressResponse("12", "Rue de Rivoli", "Paris", "Ile-de-France", "75001", "France"),
                created, updated));
    }
}
