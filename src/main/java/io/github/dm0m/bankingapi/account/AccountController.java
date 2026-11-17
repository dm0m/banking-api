package io.github.dm0m.bankingapi.account;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.dm0m.bankingapi.security.IsAdmin;
import io.github.dm0m.bankingapi.security.IsAdminOrAccountOwner;
import io.github.dm0m.bankingapi.security.IsAdminOrCustomer;
import jakarta.validation.Valid;

@RestController
public class AccountController implements AccountApi {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/customers/{id}/accounts")
    @IsAdminOrCustomer
    @Override
    public ResponseEntity<AccountResponse> openAccount(@PathVariable Long id,
            @Valid @RequestBody OpenAccountRequest request) {
        AccountResponse opened = accountService.open(id, request);
        URI location = URI.create("/accounts/" + opened.id());
        return ResponseEntity.created(location).body(opened);
    }

    @GetMapping("/accounts/{id}")
    @IsAdminOrAccountOwner
    @Override
    public AccountResponse getAccountById(@PathVariable Long id) {
        return accountService.getAccountById(id);
    }

    @GetMapping("/customers/{id}/accounts")
    @IsAdminOrCustomer
    @Override
    public List<AccountResponse> getAccountsByCustomerId(@PathVariable Long id) {
        return accountService.getAccountsByCustomerId(id);
    }

    @PostMapping("/accounts/{id}/deposit")
    @IsAdmin
    @Override
    public AccountResponse deposit(@PathVariable Long id, @Valid @RequestBody DepositRequest request) {
        return accountService.deposit(id, request);
    }

    @PostMapping("/accounts/{id}/close")
    @IsAdminOrAccountOwner
    @Override
    public AccountResponse closeAccount(@PathVariable Long id) {
        return accountService.close(id);
    }
}
