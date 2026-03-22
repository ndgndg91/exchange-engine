#!/bin/bash

# Load common configurations
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SOURCE_DIR/common.sh"

echo "=================================================="
echo "Fill Or Kill (FOK) Order Test (Strict Failure Case)"
echo "=================================================="

# 0. 입금 (충분히)
echo -e "\n[Step 0] Depositing Funds..."
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 300, "currency_id": 1, "amount": 100000000000}' > /dev/null
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 301, "currency_id": 2, "amount": 10000000000000}' > /dev/null
sleep 1

# Scenario 3: Real Kill Test (Liquidity 5 < Order 100)
echo -e "\n[Scenario 3] FOK Kill Test (Extreme Quantity)"
echo " > Current OrderBook State (May have leftovers from previous tests):"
curl -s "$BASE_URL/orderbook?symbolId=1" | python3 -m json.tool

echo " > Placing 5 BTC Maker Sell at 60000..."
curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 300, "symbol_id": 1, "price": 60000, "qty": 500000000, "side": 2, "order_type": 1}' > /dev/null
sleep 1

echo " > Placing 100 BTC FOK Buy at 60000 (Liquidity is way too low)..."
# qty: 100억 (100 BTC)
RESPONSE=$(curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 301, "symbol_id": 1, "price": 60000, "qty": 10000000000, "side": 1, "order_type": 1, "tif": 2}')
echo " > Response: $RESPONSE"
sleep 1

echo " > Final OrderBook State (Should still show 5 BTC at 60000):"
curl -s "$BASE_URL/orderbook?symbolId=1" | python3 -m json.tool
