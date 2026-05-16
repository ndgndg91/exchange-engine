#!/bin/bash

# Load common configurations
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SOURCE_DIR/common.sh"

echo "=================================================="
echo "IOC Order Test (JSON)"
echo "=================================================="

# 0. 입금
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 100, "currency_id": 1, "amount": 1000000000}' > /dev/null
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 101, "currency_id": 2, "amount": 100000000000}' > /dev/null
sleep 0.5

# 1. 매도 주문 (10 BTC @ 50000)
curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 100, "symbol_id": 1, "price": 50000, "qty": 1000000000, "side": 2}' > /dev/null
echo " > Limit Sell 10 BTC @ 50000 placed."
sleep 0.5

# 2. IOC 매수 주문 (20 BTC @ 50000) -> 10개만 체결되고 10개는 취소되어야 함
echo " > Placing Buy 20 BTC @ 50000 (IOC)..."
# tif: 1 (IOC)
RESPONSE=$(curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 101, "symbol_id": 1, "price": 50000, "qty": 2000000000, "side": 1, "order_type": 1, "tif": 1}')
echo " > Response: $RESPONSE"
sleep 0.5

# 3. 오더북 확인
echo -e "\n[Step 3] Checking OrderBook..."
curl -s "$BASE_URL/orderbook?symbolId=1" | python3 -m json.tool
echo " > Expected: Empty Asks (10 filled) and Empty Bids (10 cancelled)"
