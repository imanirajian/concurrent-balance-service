package com.imanirajian.finance.cbs.api.controller;

import com.imanirajian.finance.cbs.service.BalanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:21 AM
 */

@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

    private final BalanceService service;

    @PostMapping
    public ResponseEntity<Void> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        service.createAccount(request.accountId(), request.initialBalance());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{accountId}/credit")
    public ResponseEntity<Void> credit(@PathVariable String accountId, @Valid @RequestBody AmountRequest request) {
        service.credit(accountId, request.amount(), request.transactionId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{accountId}/debit")
    public ResponseEntity<Void> debit(@PathVariable String accountId, @Valid @RequestBody AmountRequest request) {
        service.debit(accountId, request.amount(), request.transactionId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse balance(@PathVariable String accountId) {
        return new BalanceResponse(accountId, service.getBalance(accountId));
    }

    public record CreateAccountRequest(@NotBlank String accountId, @PositiveOrZero long initialBalance) {
    }

    public record AmountRequest(@Positive long amount, @NotBlank String transactionId) {
    }

    public record BalanceResponse(String accountId, long balance) {
    }

}
