package io.github.dm0m.bankingapi.transfer;

import java.net.URI;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class TransferController implements TransferApi {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PreAuthorize("hasRole('ADMIN') or @access.ownsAccount(authentication, #transferRequest.fromAccountId())")
    @PostMapping("/transfers")
    @Override
    public ResponseEntity<TransferResponse> initiateTransfer(@Valid @RequestBody TransferRequest transferRequest) {
        TransferResponse transferResponse = transferService.transfer(transferRequest);
        URI location = URI.create("/transfers/" + transferResponse.id());
        return ResponseEntity.created(location).body(transferResponse);
    }

    @PreAuthorize("hasRole('ADMIN') or @access.canSeeTransfer(authentication, #id)")
    @GetMapping("/transfers/{id}")
    @Override
    public TransferResponse getTransferById(@PathVariable Long id) {
        return transferService.getTransferById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or @access.ownsAccount(authentication, #accountId)")
    @GetMapping("/accounts/{accountId}/transfers")
    @Override
    public Page<TransferResponse> getTransfersByAccountId(@PathVariable Long accountId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transferService.getTransfersByAccountId(accountId, pageable);
    }
}
