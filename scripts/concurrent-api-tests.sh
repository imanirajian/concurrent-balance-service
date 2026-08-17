#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="${BASE_URL}/api/v1"

ACCOUNT="CONCURRENT"
INITIAL=100000
OPERATIONS=1000
AMOUNT=100

echo "Creating account..."

curl -fsS \
  -X POST \
  "${API}/account" \
  -H 'Content-Type: application/json' \
  -d "{\"accountId\":\"${ACCOUNT}\",\"initialBalance\":${INITIAL}}" \
  > /dev/null

echo "Sending ${OPERATIONS} concurrent debits..."

seq 1 "${OPERATIONS}" |
  xargs -P 32 -I {} \
  curl -fsS \
  -X POST \
  "${API}/account/${ACCOUNT}/debit" \
  -H 'Content-Type: application/json' \
  -d "{\"amount\":${AMOUNT},\"transactionId\":\"DEBIT-{}\"}" \
  > /dev/null

BALANCE=$(
  curl -fsS \
    "${API}/account/${ACCOUNT}/balance"
)

echo "Final balance:"
echo "${BALANCE}"

EXPECTED=$((INITIAL - OPERATIONS * AMOUNT))

echo "Expected: ${EXPECTED}"