package io.github.dm0m.bankingapi.exception;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Extends ResponseEntityExceptionHandler so Spring MVC errors (malformed JSON, wrong parameter type,
// unknown route, wrong method...) are answered here too, as ProblemDetail like everything else.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Postgres "numeric value out of range", e.g. a balance pushed past NUMERIC(19,4)
    private static final String NUMERIC_VALUE_OUT_OF_RANGE = "22003";

    // domain exceptions, grouped by HTTP status; each message is safe to show to the client
    @ExceptionHandler({CustomerNotFoundException.class, AccountNotFoundException.class, TransferNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({InvalidAmountException.class, AccountsNotDistinctException.class})
    public ProblemDetail handleBadRequest(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // the request is valid but clashes with the resource's current state
    @ExceptionHandler({AccountNotClosableException.class, EmailAlreadyExistsException.class, InactiveAccountException.class})
    public ProblemDetail handleConflictWithCurrentState(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // well-formed request that a business rule refuses
    @ExceptionHandler({InsufficientFundsException.class, CurrencyMismatchException.class, UnsupportedCountryException.class})
    public ProblemDetail handleBusinessRuleViolation(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    // another request got in the way: @Version mismatch, row lock timeout, deadlock or serialization failure;
    // all are transient, so the client is told to retry rather than getting a 500
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ProblemDetail handleConcurrencyFailure(ConcurrencyFailureException ex) {
        logger.info("Concurrency failure: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "the resource was modified by another request, retry");
    }

    // e.g. ?sort=foo on a paged endpoint: a client error, not a server failure
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleInvalidSortProperty(PropertyReferenceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "invalid sort property '" + ex.getPropertyName() + "'");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        if (NestedExceptionUtils.getMostSpecificCause(ex) instanceof SQLException sql
                && NUMERIC_VALUE_OUT_OF_RANGE.equals(sql.getSQLState())) {
            logger.info("Numeric value out of range: {}", sql.getMessage());
            return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                    "the resulting balance exceeds the supported range");
        }
        logger.warn("Data integrity violation", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Request conflicts with existing data");
    }

    // @Valid failures: list every rejected field instead of the generic "Invalid request content.";
    // a class-level constraint has no field, so it is reported under the object's name
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        @NonNull MethodArgumentNotValidException ex,
        @NonNull HttpHeaders headers,
        @NonNull HttpStatusCode status,
        @NonNull WebRequest request) {
            ProblemDetail problem = ex.getBody();
            problem.setDetail("request validation failed");
            List<Map<String, String>> errors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> Map.of(
                    "field", error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                    "message", String.valueOf(error.getDefaultMessage())))
                .toList();
            problem.setProperty("errors", errors);
            return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // last resort: log the details for us, tell the client nothing about the internals
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedErrors(Exception ex) {
        logger.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "unexpected error");
    }
}
