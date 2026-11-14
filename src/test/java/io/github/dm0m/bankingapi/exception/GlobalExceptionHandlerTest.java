package io.github.dm0m.bankingapi.exception;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.dm0m.bankingapi.controller.TransferController;
import io.github.dm0m.bankingapi.dto.TransferRequest;
import io.github.dm0m.bankingapi.model.Account;
import io.github.dm0m.bankingapi.service.TransferService;

// Every failure must come back as a ProblemDetail (application/problem+json), never a stack trace.
// The web layer only: the service is mocked, so each error can be triggered directly.
@WebMvcTest(TransferController.class)
class GlobalExceptionHandlerTest {

    private static final String VALID_TRANSFER = "{\"fromAccountId\": 1, \"toAccountId\": 2, \"amount\": 30}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private TransferService transferService;

    @Test
    void malformedJson_returns400() throws Exception {
        postTransfer("{\"fromAccountId\": 1,")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void invalidBody_returns400ListingEveryRejectedField() throws Exception {
        postTransfer("{\"amount\": -5}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("request validation failed"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("fromAccountId", "toAccountId", "amount")))
                .andExpect(jsonPath("$.errors[?(@.field == 'amount')].message").value("must be greater than 0"));
    }

    @Test
    void pathVariableOfWrongType_returns400() throws Exception {
        mockMvc.perform(get("/transfers/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void wrongContentType_returns415() throws Exception {
        mockMvc.perform(post("/transfers").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void wrongMethod_returns405() throws Exception {
        mockMvc.perform(delete("/transfers/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownRoute_returns404() throws Exception {
        mockMvc.perform(get("/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void concurrentModification_returns409() throws Exception {
        when(transferService.transfer(any(TransferRequest.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Account.class, 1L));

        postTransfer(VALID_TRANSFER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("the resource was modified by another request, retry"));
    }

    @Test
    void lockNotAcquired_returns409() throws Exception {
        // what Postgres's "deadlock detected" (40P01) or a lock timeout becomes
        when(transferService.transfer(any(TransferRequest.class)))
                .thenThrow(new CannotAcquireLockException("deadlock detected"));

        postTransfer(VALID_TRANSFER)
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("the resource was modified by another request, retry"));
    }

    @Test
    void balanceOutOfRange_returns422() throws Exception {
        when(transferService.transfer(any(TransferRequest.class)))
                .thenThrow(new DataIntegrityViolationException("could not execute statement",
                        new SQLException("ERROR: numeric field overflow", "22003")));

        postTransfer(VALID_TRANSFER)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("the resulting balance exceeds the supported range"));
    }

    @Test
    void otherIntegrityViolation_returns409() throws Exception {
        when(transferService.transfer(any(TransferRequest.class)))
                .thenThrow(new DataIntegrityViolationException("could not execute statement",
                        new SQLException("ERROR: duplicate key value", "23505")));

        postTransfer(VALID_TRANSFER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Request conflicts with existing data"));
    }

    @Test
    void unexpectedError_returns500WithoutLeakingInternals() throws Exception {
        when(transferService.transfer(any(TransferRequest.class)))
                .thenThrow(new IllegalStateException("ERROR: numeric field overflow at table accounts"));

        postTransfer(VALID_TRANSFER)
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("unexpected error"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(Matchers.containsString("overflow"))));
    }

    private ResultActions postTransfer(String body) throws Exception {
        return mockMvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
