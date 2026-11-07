package io.github.dm0m.bankingapi.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.dm0m.bankingapi.dto.CreateCustomerRequest;
import io.github.dm0m.bankingapi.dto.CustomerResponse;
import io.github.dm0m.bankingapi.dto.UpdateCustomerRequest;
import io.github.dm0m.bankingapi.exception.CustomerNotFoundException;
import io.github.dm0m.bankingapi.exception.EmailAlreadyExistsException;
import io.github.dm0m.bankingapi.mapper.AddressMapper;
import io.github.dm0m.bankingapi.mapper.CustomerMapper;
import io.github.dm0m.bankingapi.model.Customer;
import io.github.dm0m.bankingapi.repository.CustomerRepository;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final AddressMapper addressMapper;

    public CustomerService(CustomerRepository customerRepository, CustomerMapper customerMapper,
            AddressMapper addressMapper) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.addressMapper = addressMapper;
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long id) {
        return customerMapper.toResponse(findCustomer(id));
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        if (customerRepository.existsByEmail(normalizeEmail(request.email()))) {
            throw new EmailAlreadyExistsException();
        }
        Customer customer = customerRepository.save(customerMapper.toEntity(request));
        return customerMapper.toResponse(customer);
    }

    @Transactional
    public CustomerResponse update(Long id, UpdateCustomerRequest request) {
        Customer customer = findCustomer(id);
        if (request.firstName() != null) customer.setFirstName(request.firstName());
        if (request.lastName() != null) customer.setLastName(request.lastName());
        if (request.phone() != null) customer.setPhone(request.phone());
        if (request.email() != null) {
            String email = normalizeEmail(request.email());
            if (!email.equals(customer.getEmail())) {
                if (customerRepository.existsByEmail(email)) {
                    throw new EmailAlreadyExistsException();
                }
                customer.setEmail(email);
            }
        }
        if (request.address() != null) {
            // update the existing address row in place instead of creating a new one
            addressMapper.updateEntity(customer.getAddress(), request.address());
        }
        // no save() needed: the entity is managed. Flush now rather than at commit so auditing
        // sets updatedAt before the response is built.
        customerRepository.flush();
        return customerMapper.toResponse(customer);
    }

    private Customer findCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
