# Design Decisions

## 1. Concurrency Handling

The wallet balance is protected using a database-level pessimistic write lock.

`WalletRepository` uses `PESSIMISTIC_WRITE` with `findByUserIdForUpdate()`.

The transaction processing method is also marked with `@Transactional`.

This ensures that concurrent debit requests for the same wallet are processed one at a time. The balance is checked while the wallet is locked, preventing multiple requests from causing the wallet balance to become negative.

## 2. Idempotency

Each transaction has a unique `transactionId`.

The `transactions` table has a unique constraint on `transactionId`.

Before processing a transaction, the service checks whether the transaction ID has already been processed.

If the transaction already exists, the request is rejected as a duplicate and the wallet balance is not deducted again.

For concurrent identical requests, the wallet lock serializes the requests. The first request succeeds, while subsequent requests detect the existing transaction.

## 3. Insufficient Funds

Before deducting money, the service compares the requested amount with the current wallet balance.

If the balance is insufficient, an `InsufficientFundsException` is thrown and no successful transaction is created.

Because the wallet is locked during this operation, simultaneous debit requests cannot all pass the balance check.

## 4. Database Choice

H2 in-memory database is used because the assignment requires zero-configuration tests.

The application uses:

`jdbc:h2:mem:walletdb`

The database schema is recreated for the test/application lifecycle.

## 5. AI Assistant Correction

An earlier AI-assisted suggestion used a file-based H2 database for manual testing.

That was not suitable for the final assignment because the assignment explicitly requires an in-memory H2 database.

The final configuration was changed to:

`jdbc:h2:mem:walletdb`

This keeps the project zero-configuration and suitable for automated tests.

## 6. Testing

The solution includes tests for:

- A single valid debit transaction.
- Three concurrent requests with the same transaction ID.
- Ten concurrent debit requests against a wallet containing ₹500.

The concurrency tests verify both idempotency and prevention of negative wallet balances.