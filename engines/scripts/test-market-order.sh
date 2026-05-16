#!/bin/bash

# Load common configurations
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SOURCE_DIR/common.sh"

echo "=================================================="
echo "Market Order Test (JSON)"
echo "User 100: Seller (Maker)"
echo "User 101: Buyer (Taker - Market Order)"
echo "=================================================="

# 0. 입금
echo -e "\n[Step 0] Depositing Funds..."
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 100, "currency_id": 1, "amount": 3000000000}' > /dev/null
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 101, "currency_id": 2, "amount": 10000000000}' > /dev/null
echo " > Deposited Funds."
sleep 0.5

# 1. 매도 주문 (Limit Sell) - 호가 조성
echo -e "\n[Step 1] Placing Limit Sell Orders (Liquidity)..."
curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 100, "symbol_id": 1, "price": 50000, "qty": 1000000000, "side": 2}' > /dev/null
curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 100, "symbol_id": 1, "price": 51000, "qty": 1000000000, "side": 2}' > /dev/null
echo " > Limit Sell 10 BTC @ 50000 and @ 51000"
sleep 0.5

echo -e "\n[Step 2] OrderBook Before Market Buy"
curl -s "$BASE_URL/orderbook?symbolId=1" | python3 -m json.tool

# 2. 시장가 매수 (Market Buy)
echo -e "\n[Step 3] Placing Market Buy Order..."
# order_type: 2 (Market)
RESPONSE=$(curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 101, "symbol_id": 1, "price": 0, "qty": 1500000000, "side": 1, "order_type": 2}')
echo " > Market Buy 15 BTC Sent: $RESPONSE"
sleep 0.5

# 3. 결과 확인
echo -e "\n[Step 4] OrderBook After Market Buy"
curl -s "$BASE_URL/orderbook?symbolId=1" | python3 -m json.tool
echo " > Expected: 5 BTC remaining at 51000"
