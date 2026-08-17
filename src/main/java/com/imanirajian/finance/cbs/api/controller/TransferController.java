package com.imanirajian.finance.cbs.api.controller;

import com.imanirajian.finance.cbs.service.BalanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:26 AM
 */

@RestController
@RequestMapping("/api/v1/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final BalanceService service;

    @PostMapping
    public ResponseEntity<Void> transfer(@Valid @RequestBody TransferRequest r) {
        service.transfer(r.sourceAccountId(), r.destinationAccountId(), r.amount(), r.transactionId());
        return ResponseEntity.noContent().build();
    }

    public record TransferRequest(@NotBlank String sourceAccountId, @NotBlank String destinationAccountId,
                                  @Positive long amount, @NotBlank String transactionId) {
    }

}

