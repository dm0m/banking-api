package io.github.dm0m.bankingapi.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.dm0m.bankingapi.dto.AccountResponse;
import io.github.dm0m.bankingapi.dto.DepositRequest;
import io.github.dm0m.bankingapi.dto.OpenAccountRequest;
import io.github.dm0m.bankingapi.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Accounts", description = "Open, read, fund and close accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Open an account for a customer")
    @ApiResponse(responseCode = "201", description = "Account opened with a zero balance; Location header points to it")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "404", description = "Customer not found")
    @ApiResponse(responseCode = "422", description = "No IBAN format for the customer's country")
    @PostMapping("/customers/{customerId}/accounts")
    public ResponseEntity<AccountResponse> openAccount(@PathVariable Long customerId,
            @Valid @RequestBody OpenAccountRequest request) {
        AccountResponse opened = accountService.open(customerId, request);
        URI location = URI.create("/accounts/" + opened.id());
        return ResponseEntity.created(location).body(opened);
    }

    @Operation(summary = "Get an account by id")
    @ApiResponse(responseCode = "200", description = "Account found")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @GetMapping("/accounts/{id}")
    public AccountResponse getAccountById(@PathVariable Long id) {
        return accountService.getAccountById(id);
    }

    @Operation(summary = "List a customer's accounts")
    @ApiResponse(responseCode = "200", description = "The customer's accounts, possibly empty")
    @ApiResponse(responseCode = "404", description = "Customer not found")
    @GetMapping("/customers/{customerId}/accounts")
    public List<AccountResponse> getAccountsByCustomerId(@PathVariable Long customerId) {
        return accountService.getAccountsByCustomerId(customerId);
    }

    @Operation(summary = "Deposit money into an account")
    @ApiResponse(responseCode = "200", description = "Deposit applied; returns the updated account")
    @ApiResponse(responseCode = "400", description = "Amount missing, not positive or more than 4 decimals")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "409", description = "Account is not active, or locked by another request; retry")
    @ApiResponse(responseCode = "422", description = "The resulting balance would exceed the supported range")
    @PostMapping("/accounts/{id}/deposit")
    public AccountResponse deposit(@PathVariable Long id, @Valid @RequestBody DepositRequest request) {
        return accountService.deposit(id, request);
    }

    @Operation(summary = "Close an account",
            description = "Only an account with a zero balance can be closed.")
    @ApiResponse(responseCode = "200", description = "Account closed")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "409", description = "Account already closed, balance not zero, or account changed by another request")
    @PostMapping("/accounts/{id}/close")
    public AccountResponse closeAccount(@PathVariable Long id) {
        return accountService.close(id);
    }
}
