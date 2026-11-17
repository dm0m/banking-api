package io.github.dm0m.bankingapi.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;

import io.github.dm0m.bankingapi.dto.AccountResponse;
import io.github.dm0m.bankingapi.dto.DepositRequest;
import io.github.dm0m.bankingapi.dto.OpenAccountRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Accounts", description = "Open, read, fund and close accounts")
public interface AccountApi {

    @Operation(summary = "Open an account for a customer")
    @ApiResponse(responseCode = "201", description = "Account opened with a zero balance; Location header points to it")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "403", description = "Not your customer record")
    @ApiResponse(responseCode = "404", description = "Customer not found")
    @ApiResponse(responseCode = "422", description = "No IBAN format for the customer's country")
    ResponseEntity<AccountResponse> openAccount(Long id, OpenAccountRequest request);

    @Operation(summary = "Get an account by id")
    @ApiResponse(responseCode = "200", description = "Account found")
    @ApiResponse(responseCode = "403", description = "Not your account")
    @ApiResponse(responseCode = "404", description = "Account not found")
    AccountResponse getAccountById(Long id);

    @Operation(summary = "List a customer's accounts")
    @ApiResponse(responseCode = "200", description = "The customer's accounts, possibly empty")
    @ApiResponse(responseCode = "403", description = "Not your customer record")
    @ApiResponse(responseCode = "404", description = "Customer not found")
    List<AccountResponse> getAccountsByCustomerId(Long id);

    @Operation(summary = "Deposit money into an account")
    @ApiResponse(responseCode = "200", description = "Deposit applied; returns the updated account")
    @ApiResponse(responseCode = "400", description = "Amount missing, not positive or more than 4 decimals")
    @ApiResponse(responseCode = "403", description = "Admins only")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "409", description = "Account is not active, or locked by another request; retry")
    @ApiResponse(responseCode = "422", description = "The resulting balance would exceed the supported range")
    AccountResponse deposit(Long id, DepositRequest request);

    @Operation(summary = "Close an account",
            description = "Only an account with a zero balance can be closed.")
    @ApiResponse(responseCode = "200", description = "Account closed")
    @ApiResponse(responseCode = "403", description = "Not your account")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "409", description = "Account already closed, balance not zero, or account changed by another request")
    AccountResponse closeAccount(Long id);
}
