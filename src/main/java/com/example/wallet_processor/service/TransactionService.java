package com.example.wallet_processor.service;

import com.example.wallet_processor.dto.TransactionRequest;
import com.example.wallet_processor.entity.Transaction;
import com.example.wallet_processor.entity.Wallet;
import com.example.wallet_processor.exception.InsufficientFundsException;
import com.example.wallet_processor.repository.TransactionRepository;
import com.example.wallet_processor.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransactionService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(WalletRepository walletRepository,
                              TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction processTransaction(TransactionRequest request) {

        /*
         * Lock the wallet first.
         *
         * This is important for concurrent debit requests.
         * Only one transaction can modify this wallet at a time.
         */
        Wallet wallet = walletRepository
                .findByUserIdForUpdate(request.getUserId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Wallet not found"));

        /*
         * Check whether this transaction was already processed.
         *
         * Because the wallet is locked first, concurrent duplicate
         * requests for the same user are serialized.
         */
        var existingTransaction =
                transactionRepository.findByTransactionId(request.getTransactionId());

        if (existingTransaction.isPresent()) {
            throw new IllegalStateException(
                    "Transaction already processed: "
                            + request.getTransactionId()
            );
        }

        /*
         * Currently the assignment requires DEBIT.
         */
        if (!"DEBIT".equalsIgnoreCase(request.getType())) {
            throw new IllegalArgumentException(
                    "Only DEBIT transactions are supported"
            );
        }

        /*
         * Prevent negative balance.
         */
        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available balance: "
                            + wallet.getBalance()
            );
        }

        /*
         * Deduct the amount.
         */
        BigDecimal newBalance =
                wallet.getBalance().subtract(request.getAmount());

        wallet.setBalance(newBalance);

        walletRepository.save(wallet);

        /*
         * Record the transaction.
         */
        Transaction transaction = new Transaction(
                request.getTransactionId(),
                request.getUserId(),
                request.getAmount(),
                request.getType().toUpperCase(),
                "SUCCESS"
        );

        return transactionRepository.save(transaction);
    }
}