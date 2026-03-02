#!/bin/bash

BASE_URL="http://127.0.0.1:8080"

echo "=================================================="
echo "Stop Order Test (Stop-Market JSON)"
echo "=================================================="

# 0. 입금
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 100, "currency_id": 1, "amount": 1000000000}' > /dev/null
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 101, "currency_id": 2, "amount": 100000000000}' > /dev/null
sleep 0.5

# 1. 유동성 공급 (Buy Maker at 50,000)
curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 101, "symbol_id": 1, "price": 50000, "qty": 100000000, "side": 1}' > /dev/null
sleep 0.1

# 2. 스탑 주문 등록 (Stop-Market Sell, Trigger <= 50,000)
# order_type: 4 (StopMarket), trigger_price: 50000
echo " > Placing Stop-Market Sell (Trigger <= 50,000)..."
curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 100, "symbol_id": 1, "price": 0, "qty": 100000000, "side": 2, "order_type": 4, "trigger_price": 50000}' > /dev/null
echo " > Stop Order Registered."
sleep 0.5

# 3. 트리거 발생 (50,000원에 체결 발생시키기)
echo " > Triggering Stop by making a trade at 50,000..."
curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 100, "symbol_id": 1, "price": 50000, "qty": 100000000, "side": 2}' > /dev/null
sleep 1

echo -e "\n[Step 4] Checking Logs..."
echo "--------------------------------------------------"
echo "Check 'me.log' for: 'STOP TRIGGERED! Order #... '"
echo "Check 'me.log' for: 'MATCH' resulting from the stop order."
echo "--------------------------------------------------"
