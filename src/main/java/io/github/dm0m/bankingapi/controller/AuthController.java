package io.github.dm0m.bankingapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.dm0m.bankingapi.dto.LoginRequest;
import io.github.dm0m.bankingapi.dto.RegisterRequest;
import io.github.dm0m.bankingapi.dto.TokenResponse;
import io.github.dm0m.bankingapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Auth", description = "Activate a login and get an access token")
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Activate a login",
            description = "Sets the password using the one-time activation code issued for the customer.")
    @ApiResponse(responseCode = "204", description = "Login activated")
    @ApiResponse(responseCode = "400", description = "Invalid request body, or invalid or expired activation code")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    @Operation(summary = "Log in", description = "Returns a Bearer access token.")
    @ApiResponse(responseCode = "200", description = "Logged in")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
