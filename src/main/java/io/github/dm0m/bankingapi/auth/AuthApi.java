package io.github.dm0m.bankingapi.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@SecurityRequirements()
@Tag(name = "Auth", description = "Activate a login and get an access token")
public interface AuthApi {

    @Operation(summary = "Activate a login",
            description = "Sets the password using the one-time activation code issued for the customer.")
    @ApiResponse(responseCode = "204", description = "Login activated")
    @ApiResponse(responseCode = "400", description = "Invalid request body, or invalid or expired activation code")
    void register(RegisterRequest request);

    @Operation(summary = "Log in", description = "Returns a Bearer access token.")
    @ApiResponse(responseCode = "200", description = "Logged in")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    TokenResponse login(LoginRequest request);
}
