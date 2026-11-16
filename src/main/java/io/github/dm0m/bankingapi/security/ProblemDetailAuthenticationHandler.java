package io.github.dm0m.bankingapi.security;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ProblemDetailAuthenticationHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        write(request, response, HttpStatus.FORBIDDEN, "access denied");
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        // a token was sent but failed to decode or validate; otherwise there was no token at all
        boolean badToken = authException instanceof OAuth2AuthenticationException;
        String detail = badToken
            ? "invalid or expired token"
            : "authentication required";
        response.setHeader(
            HttpHeaders.WWW_AUTHENTICATE,
            badToken
                ? "Bearer error=\"invalid_token\", error_description=\"invalid or expired token\""
                : "Bearer"
        );
        write(request, response, HttpStatus.UNAUTHORIZED, detail);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatusCode status, String detail) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
