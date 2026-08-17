#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="${BASE_URL}/api/v1"

request() {
  curl --fail-with-body -sS -i "$@"
  printf '\n\n'
}

echo '=== Concurrent Balance Service API smoke tests ==='
echo "BASE_URL=${BASE_URL}"
echo

echo '1) Create accounts A and B'
request -X POST "${API}/account" -H 'Content-Type: application/json' -d '{"accountId":"A","initialBalance":1000}'
request -X POST "${API}/account" -H 'Content-Type: application/json' -d '{"accountId":"B","initialBalance":500}'

echo '2) Read balances'
request "${API}/account/A/balance"
request "${API}/account/B/balance"

echo '3) Credit A by 500'
request -X POST "${API}/account/A/credit" -H 'Content-Type: application/json' -d '{"amount":500,"transactionId":"TX-CREDIT-001"}'

echo '4) Retry the same credit (must be idempotent)'
request -X POST "${API}/account/A/credit" -H 'Content-Type: application/json' -d '{"amount":500,"transactionId":"TX-CREDIT-001"}'
request "${API}/account/A/balance"

echo '5) Debit A by 300 and retry it'
request -X POST "${API}/account/A/debit" -H 'Content-Type: application/json' -d '{"amount":300,"transactionId":"TX-DEBIT-001"}'
request -X POST "${API}/account/A/debit" -H 'Content-Type: application/json' -d '{"amount":300,"transactionId":"TX-DEBIT-001"}'
request "${API}/account/A/balance"

echo '6) Transfer 300 from A to B and retry it'
request -X POST "${API}/transfer" -H 'Content-Type: application/json' -d '{"sourceAccountId":"A","destinationAccountId":"B","amount":300,"transactionId":"TX-TRANSFER-001"}'
request -X POST "${API}/transfer" -H 'Content-Type: application/json' -d '{"sourceAccountId":"A","destinationAccountId":"B","amount":300,"transactionId":"TX-TRANSFER-001"}'
request "${API}/account/A/balance"
request "${API}/account/B/balance"

echo '7) Reusing a transaction ID with a different payload must fail'

curl -sS -i \
  -X POST \
  "${API}/account/A/credit" \
  -H 'Content-Type: application/json' \
  -d '{
        "amount":999,
        "transactionId":"TX-CREDIT-001"
      }'

printf '\n\n'

echo '8) Validation / business errors (expected HTTP errors)'
set +e
curl -sS -i -X POST "${API}/account/A/credit" -H 'Content-Type: application/json' -d '{"amount":0,"transactionId":"TX-INVALID-001"}'; printf '\n\n'
curl -sS -i -X POST "${API}/account/A/debit" -H 'Content-Type: application/json' -d '{"amount":999999999,"transactionId":"TX-INSUFFICIENT-001"}'; printf '\n\n'
curl -sS -i "${API}/account/UNKNOWN/balance"; printf '\n\n'
curl -sS -i -X POST "${API}/transfer" -H 'Content-Type: application/json' -d '{"sourceAccountId":"A","destinationAccountId":"A","amount":100,"transactionId":"TX-SELF-001"}'; printf '\n\n'
curl -sS -i -X POST "${API}/account/A/credit" -H 'Content-Type: application/json' -d '{"amount":999,"transactionId":"TX-CREDIT-001"}'; printf '\n\n'
set -e

echo '9) Health'
request "${BASE_URL}/actuator/health"

echo '=== Done ==='