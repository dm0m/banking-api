package io.github.dm0m.bankingapi.customer;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.dm0m.bankingapi.auth.ActivationCodeResponse;
import io.github.dm0m.bankingapi.auth.AuthService;
import io.github.dm0m.bankingapi.security.IsAdmin;
import io.github.dm0m.bankingapi.security.IsAdminOrCustomer;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/customers")
public class CustomerController implements CustomerApi {

    private final CustomerService customerService;
    private final AuthService authService;

    public CustomerController(CustomerService customerService, AuthService authService) {
        this.customerService = customerService;
        this.authService = authService;
    }
    
    @IsAdminOrCustomer
    @GetMapping("/{id}")
    @Override
    public CustomerResponse getCustomerById(@PathVariable Long id) {
        return customerService.getCustomerById(id);
    }

    @IsAdminOrCustomer
    @PatchMapping("/{id}")
    @Override
    public CustomerResponse updateCustomer(@PathVariable Long id, @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.update(id, request);
    }

    @IsAdmin
    @PostMapping
    @Override
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = customerService.create(request);
        URI location = URI.create("/customers/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/{id}/activation-code")
    @IsAdmin
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ActivationCodeResponse issueActivationCode(@PathVariable Long id) {
        return authService.issueActivationCode(id);
    }
}
