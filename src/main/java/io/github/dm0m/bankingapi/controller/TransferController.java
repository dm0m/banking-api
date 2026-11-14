package io.github.dm0m.bankingapi.controller;

import java.net.URI;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.dm0m.bankingapi.dto.TransferRequest;
import io.github.dm0m.bankingapi.dto.TransferResponse;
import io.github.dm0m.bankingapi.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Transfers", description = "Move money between accounts and read transfer history")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @Operation(summary = "Transfer money between two accounts",
            description = "Debits the source and credits the target in one transaction. Both accounts must be active and use the same currency.")
    @ApiResponse(responseCode = "201", description = "Transfer done; Location header points to it")
    @ApiResponse(responseCode = "400", description = "Invalid request body, non-positive amount or same account twice")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "409", description = "An account is not active, or locked by another request; retry")
    @ApiResponse(responseCode = "422", description = "Insufficient funds, different currencies, or the target balance would exceed the supported range")
    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> initiateTransfer(@Valid @RequestBody TransferRequest transferRequest) {
        TransferResponse transferResponse = transferService.transfer(transferRequest);
        URI location = URI.create("/transfers/" + transferResponse.id());
        return ResponseEntity.created(location).body(transferResponse);
    }

    @Operation(summary = "Get a transfer by id")
    @ApiResponse(responseCode = "200", description = "Transfer found")
    @ApiResponse(responseCode = "404", description = "Transfer not found")
    @GetMapping("/transfers/{id}")
    public TransferResponse getTransferById(@PathVariable Long id) {
        return transferService.getTransferById(id);
    }

    @Operation(summary = "List an account's transfers",
            description = "Sent and received transfers, newest first by default. Use page, size and sort, e.g. sort=amount,asc.")
    @ApiResponse(responseCode = "200", description = "One page of transfers")
    @ApiResponse(responseCode = "400", description = "Unknown sort property")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @GetMapping("/accounts/{accountId}/transfers")
    public Page<TransferResponse> getTransfersByAccountId(@PathVariable Long accountId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transferService.getTransfersByAccountId(accountId, pageable);
    }
}
