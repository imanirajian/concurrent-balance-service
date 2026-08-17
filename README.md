# Concurrent Balance Service
A high‑performance, thread‑safe, idempotent balance management service built with Java 21 and Spring Boot

## Architecture
                        REST API
                           │
                           ▼
                 ┌───────────────────┐
                 │ AccountController │
                 │ TransferController│
                 └────────┬──────────┘
                          │
                          ▼
                 ┌──────────────────┐
                 │  BalanceService  │
                 └────────┬─────────┘
                          │
              ┌───────────┴────────────┐
              │                        │
              ▼                        ▼
      AccountRepository        IdempotencyStore
              │                        │
              ▼                        ▼
           Account             ConcurrentHashMap
              │
              ▼
        ReentrantLock

---

                 TX
                 │
                 ▼
        Idempotency Store
                 │
                 ▼
       ┌─────────────────┐
       │ Account A       │
       │ Account B       │
       └────────┬────────┘
                │
         deterministic order
                │
                ▼
             lock A
                │
                ▼
             lock B
                │
                ▼
        validate everything
                │
          ┌─────┴─────┐
          │           │
        fail        success
          │           │
          ▼           ▼
       no mutation   A -= x
                     B += x
                          │
                          ▼
                    transaction result
---
## Design
- Per-account `ReentrantLock`; no global balance lock.
- `ConcurrentHashMap` account registry.
- Transaction ID + immutable fingerprint + `CompletableFuture` for concurrent idempotency.
- Deterministic account-ID lock ordering for transfers, preventing circular-wait deadlocks.
- Business failures are retained as terminal idempotent outcomes. Unexpected runtime failures remove the in-flight entry.
- `Math.addExact` prevents silent long overflow.
- REST API, Bean Validation and Actuator included.
- Virtual threads enabled for request handling; correctness still comes from explicit account locks.

## Consistency Model

The implementation is intentionally single-JVM.

Account state and idempotency records are stored in memory. Therefore:

- concurrent operations on the same account are serialized;
- operations on independent accounts can execute concurrently;
- transfers acquire both account locks in deterministic account-ID order;
- transaction IDs are idempotent within the JVM lifetime;
- failed business transactions are also idempotent;
- state is lost when the process terminates;
- the implementation is not horizontally scalable as-is.

For a distributed deployment, the source of truth would move to a transactional
database and idempotency would be persisted transactionally with the balance
mutation.

Redis was intentionally not introduced because a distributed lock is unnecessary
for the single-JVM challenge and would introduce additional failure modes and
operational complexity.

## Concurrency

Each `Account` owns its own `ReentrantLock`.

This provides:

- mutual exclusion for mutations of the same account;
- no global application lock;
- concurrent processing of independent accounts.

For example:

    Thread 1 -> Account A
    Thread 2 -> Account B

can execute concurrently.

For transfers involving two accounts, both locks are acquired in deterministic
account-ID order:

    min(accountId) -> max(accountId)

This prevents circular lock acquisition and therefore eliminates the classic
A -> B / B -> A deadlock scenario.

Locks are always released in `finally` blocks.

## Idempotency

Every financial operation is identified by a unique `transactionId`.

The idempotency store maintains:

    transactionId
        -> transaction fingerprint
        -> transaction outcome

The fingerprint contains the complete operation identity, including:

- operation type;
- source account;
- destination account;
- amount.

Therefore:

    credit(A, 100, TX-1)
    credit(A, 100, TX-1)

is idempotent, while:

    credit(A, 100, TX-1)
    credit(A, 200, TX-1)

is rejected.

Concurrent requests using the same transaction ID share the same in-flight
transaction result rather than executing the financial operation multiple times.

Business failures are also cached. Therefore retrying the same failed transaction
returns the same outcome instead of re-evaluating it against a potentially changed
balance.

## Transfer Atomicity

Transfers acquire both account locks before performing any mutation.

All validations are performed before the first balance mutation:

1. source account exists;
2. destination account exists;
3. source has sufficient funds;
4. destination balance cannot overflow.

Only after all validations succeed:

    source.debit(amount)
    destination.credit(amount)

Because both account locks remain held for the entire mutation, no other service
operation can mutate either account during the transfer.

Account locks are acquired in deterministic order based on account ID, preventing
deadlocks caused by opposite-direction transfers.

## Build
```bash
./mvnw clean test
```

## Why not JPA/Redis/Kafka?
The challenge is primarily testing concurrency correctness. Adding distributed infrastructure would obscure the core algorithm. For a multi-instance production system, the durable source of truth should be a relational database with a unique transaction ID constraint and a database transaction/row locks; Kafka would be downstream messaging, ideally via an outbox.

## Transfer
Both account locks are acquired in lexical account-ID order. Debit and credit happen while both locks are held. A destination overflow is compensated before either lock is released.

## Same-account transfer
Rejected because it produces no meaningful state transition and avoids ambiguous transaction semantics.

## API
`POST /api/v1/account` creates an account.

`POST /api/v1/account/{id}/credit` credits.

`POST /api/v1/account/{id}/debit` debits.

`POST /api/v1/transfer` transfers.

`GET /api/v1/account/{id}/balance` reads balance.

## API smoke tests with cURL

A complete, repeatable API smoke-test script is included at:

```text
scripts/api-tests.sh
scripts/concurrent-api-tests.sh
```

Start the application first:

```bash
./mvnw spring-boot:run
```

Then run:

```bash
./scripts/api-smoke-tests.sh
./scripts/concurrent-api-tests.sh
```

On Windows Git Bash/WSL, the same command works. PowerShell users can run the individual `curl` commands below or execute the script through WSL/Git Bash.

You can override the server URL:

```bash
BASE_URL=http://localhost:9090 ./scripts/api-tests.sh
```

The script exercises account creation, balance reads, credit, debit, transfer, retry/idempotency, validation failures, unknown accounts, insufficient funds, same-account transfer, transaction-ID fingerprint protection, and Actuator health.

### Individual cURL examples

Create account:

```bash
curl -i -X POST http://localhost:8080/api/v1/account \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"A","initialBalance":1000}'
```

Get balance:

```bash
curl -i http://localhost:8080/api/v1/account/A/balance/
```

Credit:

```bash
curl -i -X POST http://localhost:8080/api/v1/account/A/credit \
  -H 'Content-Type: application/json' \
  -d '{"amount":500,"transactionId":"TX-CREDIT-001"}'
```

Debit:

```bash
curl -i -X POST http://localhost:8080/api/v1/account/A/debit \
  -H 'Content-Type: application/json' \
  -d '{"amount":300,"transactionId":"TX-DEBIT-001"}'
```

Transfer:

```bash
curl -i -X POST http://localhost:8080/api/v1/transfer \
  -H 'Content-Type: application/json' \
  -d '{"sourceAccountId":"A","destinationAccountId":"B","amount":300,"transactionId":"TX-TRANSFER-001"}'
```

Health:

```bash
curl -i http://localhost:8080/actuator/health
```

### Concurrent duplicate-request test

This specifically exercises the requirement that the same transaction can arrive concurrently:

```bash
for i in $(seq 1 100); do
  curl -sS -X POST http://localhost:8080/api/v1/account/A/credit \
    -H 'Content-Type: application/json' \
    -d '{"amount":500,"transactionId":"TX-SAME-CONCURRENT"}' &
done
wait
```

The transaction must affect the balance **exactly once**, not 100 times.

### Concurrent unique transactions

```bash
for i in $(seq 1 100); do
  curl -sS -X POST http://localhost:8080/api/v1/account/A/credit \
    -H 'Content-Type: application/json' \
    -d "{\"amount\":1,\"transactionId\":\"TX-CONCURRENT-$i\"}" &
done
wait
```

All 100 unique transactions must be reflected in the final balance.

### Concurrent transfers in opposite directions

Create two sufficiently funded accounts first, then run:

```bash
for i in $(seq 1 100); do
  curl -sS -X POST http://localhost:8080/api/v1/transfer \
    -H 'Content-Type: application/json' \
    -d "{\"sourceAccountId\":\"A\",\"destinationAccountId\":\"B\",\"amount\":1,\"transactionId\":\"TX-AB-$i\"}" &

  curl -sS -X POST http://localhost:8080/api/v1/transfer \
    -H 'Content-Type: application/json' \
    -d "{\"sourceAccountId\":\"B\",\"destinationAccountId\":\"A\",\"amount\":1,\"transactionId\":\"TX-BA-$i\"}" &
done
wait
```

This is useful for manually exercising the deterministic lock ordering that prevents the classic `A -> B` / `B -> A` deadlock.
