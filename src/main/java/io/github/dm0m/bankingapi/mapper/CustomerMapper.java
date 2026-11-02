package io.github.dm0m.bankingapi.mapper;

import org.springframework.stereotype.Component;

import io.github.dm0m.bankingapi.dto.CreateCustomerRequest;
import io.github.dm0m.bankingapi.dto.CustomerResponse;
import io.github.dm0m.bankingapi.model.Customer;

@Component
public class CustomerMapper {

    private final AddressMapper addressMapper;

    public CustomerMapper(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    public Customer toEntity(CreateCustomerRequest request) {
        Customer customer = new Customer();
        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setDob(request.dob());
        customer.setAddress(addressMapper.toEntity(request.address()));
        return customer;
    }

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
            customer.getId(),
            customer.getFirstName(),
            customer.getLastName(),
            customer.getPhone(),
            customer.getEmail(),
            customer.getDob(),
            addressMapper.toResponse(customer.getAddress()),
            customer.getCreatedAt(),
            customer.getUpdatedDate());
    }
}
