package io.github.dm0m.bankingapi.controller.api;

import org.springframework.http.ResponseEntity;

import io.github.dm0m.bankingapi.dto.ActivationCodeResponse;
import io.github.dm0m.bankingapi.dto.CreateCustomerRequest;
import io.github.dm0m.bankingapi.dto.CustomerResponse;
import io.github.dm0m.bankingapi.dto.UpdateCustomerRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Customers", description = "Create, read and update customers")
public interface CustomerApi {

    @Operation(summary = "Get a customer by id")
    @ApiResponse(responseCode = "200", description = "Customer found")
    @ApiResponse(responseCode = "403", description = "Not your customer record")
    @ApiResponse(responseCode = "404", description = "Customer not found")
    CustomerResponse getCustomerById(Long id);

    @Operation(summary = "Update a customer",
            description = "Only the fields present in the body are changed.")
    @ApiResponse(responseCode = "200", description = "Customer updated")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "403", description = "Not your customer record")
    @ApiResponse(responseCode = "404", description = "Customer not found")
    @ApiResponse(responseCode = "409", description = "Email already used by another customer")
    CustomerResponse updateCustomer(Long id, UpdateCustomerRequest request);

    @Operation(summary = "Create a customer")
    @ApiResponse(responseCode = "201", description = "Customer created; Location header points to it")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "403", description = "Admins only")
    @ApiResponse(responseCode = "409", description = "Email already used by another customer")
    ResponseEntity<CustomerResponse> createCustomer(CreateCustomerRequest request);

    @Operation(summary = "Issue an activation code",
            description = "Creates the customer's login if needed and returns a new one-time code, "
                    + "replacing any earlier one. The code is shown only in this response.")
    @ApiResponse(responseCode = "201", description = "Code issued")
    @ApiResponse(responseCode = "403", description = "Admins only")
    @ApiResponse(responseCode = "404", description = "Customer not found")
    @ApiResponse(responseCode = "409", description = "Customer already has an active login, or its email is used by another login")
    ActivationCodeResponse issueActivationCode(Long id);
}
