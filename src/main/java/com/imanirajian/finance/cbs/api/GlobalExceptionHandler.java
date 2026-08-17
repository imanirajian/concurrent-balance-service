package com.imanirajian.finance.cbs.api;

import com.imanirajian.finance.cbs.domain.ApiError;
import com.imanirajian.finance.cbs.domain.exception.AccountNotFoundException;
import com.imanirajian.finance.cbs.domain.exception.InsufficientFundsException;
import com.imanirajian.finance.cbs.domain.exception.InvalidAmountException;
import com.imanirajian.finance.cbs.domain.exception.InvalidTransactionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:27 AM
 */

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<ApiError> handleAccountNotFound(AccountNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("ACCOUNT_NOT_FOUND", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ResponseEntity<ApiError> handleInsufficientFunds(InsufficientFundsException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of("INSUFFICIENT_FUNDS", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidAmountException.class)
    ResponseEntity<ApiError> handleInvalidAmount(InvalidAmountException ex, HttpServletRequest request) {
        return ResponseEntity
                .badRequest()
                .body(ApiError.of("INVALID_AMOUNT", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidTransactionException.class)
    ResponseEntity<ApiError> handleInvalidTransaction(InvalidTransactionException ex, HttpServletRequest request) {
        return ResponseEntity
                .badRequest()
                .body(ApiError.of("INVALID_TRANSACTION", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");

        return ResponseEntity
                .badRequest()
                .body(ApiError.of("INVALID_REQUEST", message, request.getRequestURI()));
    }
}