# Idempotent Payment / Wallet Event Processor

A Spring Boot backend service that processes wallet debit transactions with idempotency and concurrency-safe balance updates.

## Technology Stack

- Java 17
- Spring Boot 3.5.16
- Spring Data JPA
- H2 In-Memory Database
- Maven
- JUnit 5

## API

### Process Transaction

**POST**

`/api/v1/transactions/process`

### Request

```json
{
  "transactionId": "11111111-1111-1111-1111-111111111111",
  "userId": "22222222-2222-2222-2222-222222222222",
  "amount": 100.00,
  "type": "DEBIT"
}


Concurrency Handling

The wallet balance is protected using a database-level pessimistic write lock.

WalletRepository uses PESSIMISTIC_WRITE with findByUserIdForUpdate().

The transaction processing method is marked with @Transactional.

This ensures that concurrent debit requests for the same wallet are processed one at a time. The balance is checked while the wallet is locked, preventing multiple requests from causing the wallet balance to become negative.

Idempotency

Each transaction has a unique transactionId.

The transactions table has a unique constraint on transactionId.

Before processing a transaction, the service checks whether the transaction ID has already been processed.

If the same transaction ID is received again, the request is rejected as a duplicate and the wallet is not debited again.

For concurrent identical requests, the wallet lock serializes the requests. The first request succeeds, while subsequent requests detect the existing transaction.

Insufficient Funds

Before deducting money, the service compares the requested amount with the current wallet balance.

If the balance is insufficient, an InsufficientFundsException is thrown and no successful transaction is created.

Because the wallet is locked during this operation, simultaneous debit requests cannot all pass the balance check.

Database

The application uses an H2 in-memory database:

jdbc:h2:mem:walletdb

No external database setup is required.

The database schema is recreated for the application/test lifecycle.

Testing

The project contains integration tests covering:

A single valid debit transaction.
Three simultaneous requests with the same transaction ID.
Ten concurrent ₹100 debit requests against a wallet with ₹500 balance.

Run the tests with:

mvn clean test
Test Results

All tests pass successfully:

Tests run: 4
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
Test 1 — Single Valid Debit
Initial balance    : ₹500.00
Debit amount       : ₹100.00
Final balance      : ₹400.00
Transaction status : SUCCESS
RESULT             : PASS
Test 2 — Concurrent Duplicate Transactions
Total requests      : 3
Successful requests : 1
Duplicate requests  : 2
Final balance       : ₹400.00
Transactions stored : 1
RESULT              : PASS
Test 3 — Concurrent Debits
Total requests          : 10
Successful requests     : 5
Insufficient funds      : 5
Final balance           : ₹0.00
Successful transactions : 5
RESULT                  : PASS
Project Structure
wallet-processor/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/wallet_processor/
│   │   │       ├── controller/
│   │   │       ├── dto/
│   │   │       ├── entity/
│   │   │       ├── exception/
│   │   │       ├── repository/
│   │   │       └── service/
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
│           └── com/example/wallet_processor/
│               ├── WalletProcessorApplicationTests.java
│               └── WalletProcessorIntegrationTest.java
├── pom.xml
├── README.md
└── DECISIONS.md

Error Handling
Condition	                 HTTP Status
Successful transaction	    200 OK
Duplicate transaction	     409 CONFLICT
Insufficient funds	         409 CONFLICT
Invalid transaction type	400 BAD REQUEST
Wallet not found	        400 BAD REQUEST
Security and Dependency Maintenance

The project uses Spring Boot 3.5.16.

The Spring Boot version was upgraded from 3.5.4 to 3.5.16 to use a newer maintained release and address known dependency security issues.

After the upgrade, the complete Maven test suite was executed successfully.

AI-Assisted Development

AI assistance was used during development for troubleshooting and design discussion.

An earlier AI-assisted suggestion used a file-based H2 database for manual testing.

This was not suitable for the assignment because the assignment requires an H2 in-memory database for zero-configuration testing.

The final configuration uses:

jdbc:h2:mem:walletdb

The detailed design decisions are documented in DECISIONS.md.