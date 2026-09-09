package com.example.wallet_processor;

import com.example.wallet_processor.dto.TransactionRequest;
import com.example.wallet_processor.entity.Transaction;
import com.example.wallet_processor.entity.Wallet;
import com.example.wallet_processor.exception.InsufficientFundsException;
import com.example.wallet_processor.repository.TransactionRepository;
import com.example.wallet_processor.repository.WalletRepository;
import com.example.wallet_processor.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WalletProcessorIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("Processes a single valid debit transaction successfully.")
    void processesSingleValidDebitTransactionSuccessfully() {

        System.out.println();
        System.out.println("==============================================");
        System.out.println("TEST 1: Single valid debit transaction");
        System.out.println("==============================================");

        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        Wallet wallet = new Wallet(
                userId,
                new BigDecimal("500.00")
        );

        walletRepository.save(wallet);

        TransactionRequest request = new TransactionRequest();
        request.setTransactionId(transactionId);
        request.setUserId(userId);
        request.setAmount(new BigDecimal("100.00"));
        request.setType("DEBIT");

        Transaction result =
                transactionService.processTransaction(request);

        Wallet updatedWallet =
                walletRepository.findById(wallet.getId()).orElseThrow();

        assertNotNull(result);
        assertEquals(transactionId, result.getTransactionId());
        assertEquals("SUCCESS", result.getStatus());

        assertEquals(
                new BigDecimal("400.00"),
                updatedWallet.getBalance()
        );

        System.out.println("Transaction status : " + result.getStatus());
        System.out.println("Initial balance    : ₹500.00");
        System.out.println("Debit amount       : ₹100.00");
        System.out.println("Final balance      : ₹"
                + updatedWallet.getBalance());
        System.out.println("RESULT             : PASS");
    }

    @Test
    @DisplayName("Sends 3 identical transactionIDs simultaneously. Ensures the balance is only deducted once.")
    void duplicateTransactionIsProcessedOnlyOnce()
            throws Exception {

        System.out.println();
        System.out.println("========================================================");
        System.out.println("TEST 2: 3 identical transaction IDs simultaneously");
        System.out.println("========================================================");

        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        Wallet wallet = new Wallet(
                userId,
                new BigDecimal("500.00")
        );

        walletRepository.save(wallet);

        TransactionRequest request = new TransactionRequest();
        request.setTransactionId(transactionId);
        request.setUserId(userId);
        request.setAmount(new BigDecimal("100.00"));
        request.setType("DEBIT");

        ExecutorService executor =
                Executors.newFixedThreadPool(3);

        CountDownLatch ready =
                new CountDownLatch(3);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < 3; i++) {

            futures.add(executor.submit(() -> {

                ready.countDown();

                start.await();

                try {
                    transactionService.processTransaction(request);
                    return "SUCCESS";
                } catch (IllegalStateException e) {
                    return "DUPLICATE";
                }
            }));
        }

        ready.await();

        start.countDown();

        int successCount = 0;
        int duplicateCount = 0;

        for (Future<String> future : futures) {

            String result = future.get();

            if ("SUCCESS".equals(result)) {
                successCount++;
            } else if ("DUPLICATE".equals(result)) {
                duplicateCount++;
            }
        }

        executor.shutdown();

        Wallet updatedWallet =
                walletRepository.findById(wallet.getId()).orElseThrow();

        assertEquals(1, successCount);
        assertEquals(2, duplicateCount);

        assertEquals(
                new BigDecimal("400.00"),
                updatedWallet.getBalance()
        );

        long transactionCount =
                transactionRepository.count();

        assertEquals(1, transactionCount);

        System.out.println("Total requests      : 3");
        System.out.println("Successful requests : " + successCount);
        System.out.println("Duplicate requests  : " + duplicateCount);
        System.out.println("Final balance       : ₹"
                + updatedWallet.getBalance());
        System.out.println("Transactions stored : " + transactionCount);
        System.out.println("RESULT              : PASS");
    }

    @Test
    @DisplayName("Sends 10 concurrent debit requests of ₹100 for a wallet with a ₹500 balance. Ensures the final balance is exactly ₹0 and 5 requests fail with insufficient funds.")
    void concurrentDebitsPreventNegativeBalance()
            throws Exception {

        System.out.println();
        System.out.println("==========================================================");
        System.out.println("TEST 3: 10 concurrent ₹100 debits on ₹500 wallet");
        System.out.println("==========================================================");

        UUID userId = UUID.randomUUID();

        Wallet wallet = new Wallet(
                userId,
                new BigDecimal("500.00")
        );

        walletRepository.save(wallet);

        ExecutorService executor =
                Executors.newFixedThreadPool(10);

        CountDownLatch ready =
                new CountDownLatch(10);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {

            UUID transactionId = UUID.randomUUID();

            TransactionRequest request =
                    new TransactionRequest();

            request.setTransactionId(transactionId);
            request.setUserId(userId);
            request.setAmount(new BigDecimal("100.00"));
            request.setType("DEBIT");

            futures.add(executor.submit(() -> {

                ready.countDown();

                start.await();

                try {
                    transactionService.processTransaction(request);
                    return "SUCCESS";

                } catch (InsufficientFundsException e) {
                    return "INSUFFICIENT_FUNDS";
                }
            }));
        }

        ready.await();

        start.countDown();

        int successCount = 0;
        int insufficientFundsCount = 0;

        for (Future<String> future : futures) {

            String result = future.get();

            if ("SUCCESS".equals(result)) {
                successCount++;
            } else if ("INSUFFICIENT_FUNDS".equals(result)) {
                insufficientFundsCount++;
            }
        }

        executor.shutdown();

        Wallet updatedWallet =
                walletRepository.findById(wallet.getId()).orElseThrow();

        assertEquals(5, successCount);
        assertEquals(5, insufficientFundsCount);

        assertEquals(
                new BigDecimal("0.00"),
                updatedWallet.getBalance()
        );

        assertTrue(
                updatedWallet.getBalance()
                        .compareTo(BigDecimal.ZERO) >= 0
        );

        long transactionCount =
                transactionRepository.count();

        assertEquals(5, transactionCount);

        System.out.println("Total requests          : 10");
        System.out.println("Successful requests     : " + successCount);
        System.out.println("Insufficient funds      : "
                + insufficientFundsCount);
        System.out.println("Final balance           : ₹"
                + updatedWallet.getBalance());
        System.out.println("Successful transactions : " + transactionCount);
        System.out.println("RESULT                  : PASS");
    }
}