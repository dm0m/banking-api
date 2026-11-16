package io.github.dm0m.bankingapi.controller.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import io.github.dm0m.bankingapi.dto.TransferRequest;
import io.github.dm0m.bankingapi.dto.TransferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Transfers", description = "Move money between accounts and read transfer history")
public interface TransferApi {

    @Operation(summary = "Transfer money between two accounts",
            description = "Debits the source and credits the target in one transaction. Both accounts must be active and use the same currency.")
    @ApiResponse(responseCode = "201", description = "Transfer done; Location header points to it")
    @ApiResponse(responseCode = "400", description = "Invalid request body, non-positive amount or same account twice")
    @ApiResponse(responseCode = "403", description = "The source account is not yours")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "409", description = "An account is not active, or locked by another request; retry")
    @ApiResponse(responseCode = "422", description = "Insufficient funds, different currencies, or the target balance would exceed the supported range")
    ResponseEntity<TransferResponse> initiateTransfer(TransferRequest transferRequest);

    @Operation(summary = "Get a transfer by id")
    @ApiResponse(responseCode = "200", description = "Transfer found")
    @ApiResponse(responseCode = "403", description = "Neither account of the transfer is yours")
    @ApiResponse(responseCode = "404", description = "Transfer not found")
    TransferResponse getTransferById(Long id);

    @Operation(summary = "List an account's transfers",
            description = "Sent and received transfers, newest first by default. Use page, size and sort, e.g. sort=amount,asc.")
    @ApiResponse(responseCode = "200", description = "One page of transfers")
    @ApiResponse(responseCode = "400", description = "Unknown sort property")
    @ApiResponse(responseCode = "403", description = "The account is not yours")
    @ApiResponse(responseCode = "404", description = "Account not found")
    Page<TransferResponse> getTransfersByAccountId(Long accountId, Pageable pageable);
}
