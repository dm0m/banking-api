package io.github.dm0m.bankingapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.dm0m.bankingapi.dto.AddressRequest;
import io.github.dm0m.bankingapi.dto.CreateCustomerRequest;
import io.github.dm0m.bankingapi.dto.CustomerResponse;
import io.github.dm0m.bankingapi.dto.UpdateCustomerRequest;
import io.github.dm0m.bankingapi.exception.CustomerNotFoundException;
import io.github.dm0m.bankingapi.exception.EmailAlreadyExistsException;
import io.github.dm0m.bankingapi.mapper.AddressMapper;
import io.github.dm0m.bankingapi.mapper.CustomerMapper;
import io.github.dm0m.bankingapi.model.Address;
import io.github.dm0m.bankingapi.model.Customer;
import io.github.dm0m.bankingapi.repository.CustomerRepository;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    private final AddressMapper addressMapper = new AddressMapper();
    private final CustomerMapper customerMapper = new CustomerMapper(addressMapper);
    private CustomerService customerService;
    private final AddressRequest address =
            new AddressRequest("12", "Rue de Rivoli", "Paris", "Ile-de-France", "75001", "FR");

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository, customerMapper, addressMapper);
    }

    private CreateCustomerRequest createRequest(String email) {
        return new CreateCustomerRequest("John", "Doe", "+33612345678", email, address, LocalDate.of(1990, 5, 15));
    }

    private Customer existingCustomer(Long id, String email) {
        Customer customer = customerMapper.toEntity(createRequest(email));
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }

    @Test
    void getCustomerById_existingCustomer_returnsResponse() {
        Customer customer = existingCustomer(1L, "john@example.com");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCustomerById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.address().city()).isEqualTo("Paris");
    }

    @Test
    void getCustomerById_unknownId_throwsCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomerById(99L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void create_newEmail_savesAndReturnsResponse() {
        when(customerRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.create(createRequest("john@example.com"));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("john@example.com");
        assertThat(saved.getValue().getAddress().getCity()).isEqualTo("Paris");
        assertThat(response.firstName()).isEqualTo("John");
        assertThat(response.email()).isEqualTo("john@example.com");
    }

    @Test
    void create_existingEmail_throwsAndDoesNotSave() {
        when(customerRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(createRequest("john@example.com")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void create_mixedCaseEmail_checksDuplicateWithLowercaseEmail() {
        when(customerRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        customerService.create(createRequest("  John@Example.COM "));

        verify(customerRepository).existsByEmail("john@example.com");
    }

    @Test
    void update_onlyNonNullFieldsChange() {
        Customer customer = existingCustomer(1L, "john@example.com");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.update(1L,
                new UpdateCustomerRequest(null, null, "+33699999999", null, null));

        assertThat(response.phone()).isEqualTo("+33699999999");
        assertThat(response.firstName()).isEqualTo("John");
        assertThat(response.lastName()).isEqualTo("Doe");
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.address().city()).isEqualTo("Paris");
        verify(customerRepository, never()).existsByEmail(any());
    }

    @Test
    void update_sameEmailInDifferentCase_skipsDuplicateCheck() {
        Customer customer = existingCustomer(1L, "john@example.com");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.update(1L,
                new UpdateCustomerRequest(null, null, null, "JOHN@example.com", null));

        assertThat(response.email()).isEqualTo("john@example.com");
        verify(customerRepository, never()).existsByEmail(any());
    }

    @Test
    void update_emailTakenByAnotherCustomer_throwsEmailAlreadyExists() {
        Customer customer = existingCustomer(1L, "john@example.com");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.update(1L,
                new UpdateCustomerRequest(null, null, null, "jane@example.com", null)))
                .isInstanceOf(EmailAlreadyExistsException.class);
        assertThat(customer.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void update_newFreeEmail_isLowercasedAndSet() {
        Customer customer = existingCustomer(1L, "john@example.com");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.existsByEmail("jane@example.com")).thenReturn(false);

        CustomerResponse response = customerService.update(1L,
                new UpdateCustomerRequest(null, null, null, "Jane@Example.COM", null));

        assertThat(response.email()).isEqualTo("jane@example.com");
    }

    @Test
    void update_address_updatesExistingAddressInPlace() {
        Customer customer = existingCustomer(1L, "john@example.com");
        Address originalAddress = customer.getAddress();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        AddressRequest newAddress =
                new AddressRequest("5", "Cours Mirabeau", "Aix-en-Provence", "Provence", "13100", "FR");

        CustomerResponse response = customerService.update(1L,
                new UpdateCustomerRequest(null, null, null, null, newAddress));

        // same object, new values: replacing it would insert a new row and break UNIQUE(address_id)
        assertThat(customer.getAddress()).isSameAs(originalAddress);
        assertThat(originalAddress.getCity()).isEqualTo("Aix-en-Provence");
        assertThat(response.address().city()).isEqualTo("Aix-en-Provence");
    }

    @Test
    void update_unknownId_throwsCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.update(99L,
                new UpdateCustomerRequest("Jane", null, null, null, null)))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
