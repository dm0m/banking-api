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
import jakarta.validation.Valid;

@RestController
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/customers/{customerId}/accounts")
    public ResponseEntity<AccountResponse> openAccount(@PathVariable Long customerId,
            @Valid @RequestBody OpenAccountRequest request) {
        AccountResponse opened = accountService.open(customerId, request);
        URI location = URI.create("/accounts/" + opened.id());
        return ResponseEntity.created(location).body(opened);
    }

    @GetMapping("/accounts/{id}")
    public AccountResponse getAccountById(@PathVariable Long id) {
        return accountService.getAccountById(id);
    }

    @GetMapping("/customers/{customerId}/accounts")
    public List<AccountResponse> getAccountsByCustomerId(@PathVariable Long customerId) {
        return accountService.getAccountsByCustomerId(customerId);
    }

    @PostMapping("/accounts/{id}/deposit")
    public AccountResponse deposit(@PathVariable Long id, @Valid @RequestBody DepositRequest request) {
        return accountService.deposit(id, request);
    }

    @PostMapping("/accounts/{id}/close")
    public AccountResponse closeAccount(@PathVariable Long id) {
        return accountService.close(id);
    }
}
