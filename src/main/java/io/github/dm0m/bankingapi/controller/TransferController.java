package io.github.dm0m.bankingapi.controller;

import java.net.URI;

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
import jakarta.validation.Valid;

@RestController 
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> initiateTransfer(@Valid @RequestBody TransferRequest transferRequest) {
        TransferResponse transferResponse = transferService.transfer(transferRequest);
        URI location = URI.create("/transfers/" + transferResponse.id());
        return ResponseEntity.created(location).body(transferResponse);
    }

    @GetMapping("/transfers/{id}")
    public TransferResponse getTransferById(@PathVariable Long id) {
        return transferService.getTransferById(id);
    }

    @GetMapping("/accounts/{accountId}/transfers")
    public Page<TransferResponse> getTransfersByAccountId(@PathVariable Long accountId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transferService.getTransfersByAccountId(accountId, pageable);
    }
}
