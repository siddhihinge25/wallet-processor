package com.example.wallet_processor.controller;

import com.example.wallet_processor.dto.TransactionRequest;
import com.example.wallet_processor.entity.Transaction;
import com.example.wallet_processor.exception.InsufficientFundsException;
import com.example.wallet_processor.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/process")
    public ResponseEntity<?> processTransaction(
            @Valid @RequestBody TransactionRequest request) {

        try {

            Transaction transaction =
                    transactionService.processTransaction(request);

            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(transaction);

        } catch (InsufficientFundsException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "status", "INSUFFICIENT_FUNDS",
                            "message", e.getMessage()
                    ));

        } catch (IllegalStateException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "status", "DUPLICATE_TRANSACTION",
                            "message", e.getMessage()
                    ));

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "status", "BAD_REQUEST",
                            "message", e.getMessage()
                    ));
        }
    }
}