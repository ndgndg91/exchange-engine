#!/bin/bash

BASE_URL="http://127.0.0.1:8080"

echo "=================================================="
echo "Order Cancel Test (JSON)"
echo "Scenario: Place Order -> Check Book -> Cancel -> Check Book"
echo "=================================================="

# 0. 입금
echo -e "\n[Step 0] Depositing Funds..."
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 100, "currency_id": 1, "amount": 1000000000}' > /dev/null
echo " > Deposited 10 BTC to User 100"
sleep 0.5

# 1. 주문 생성
echo -e "\n[Step 1] Placing Maker Order..."
RESPONSE=$(curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 100, "symbol_id": 1, "price": 50000, "qty": 100000000, "side": 2}')
echo " > Response: $RESPONSE"

ORDER_ID=$(echo $RESPONSE | awk -F': ' '{print $2}')
echo " > Extracted Order ID: $ORDER_ID"
sleep 0.5

# 2. 오더북 확인 (Before Cancel)
echo -e "\n[Step 2] OrderBook (Before Cancel)"
curl -s "$BASE_URL/orderbook?symbolId=1" | python3 -m json.tool

# 3. 주문 취소
# 현재 Gateway는 /cancel 엔드포인트를 명시적으로 구현하지 않았을 수 있음 (Post /order로 처리 확인 필요)
# OMEEngine.kt 확인 시 onCancelRequest 가 존재함. GatewayServer에 /cancel 추가 확인.
# 일단 기존 규격대로 시도.
echo -e "\n[Step 3] Cancelling Order #$ORDER_ID..."
CANCEL_RES=$(curl -s -X POST "$BASE_URL/cancel" -H "Content-Type: application/json" -d "{\"user_id\": 100, \"order_id\": $ORDER_ID, \"symbol_id\": 1}")
echo " > Cancel Response: $CANCEL_RES"
sleep 0.5

# 4. 오더북 확인 (After Cancel)
echo -e "\n[Step 4] OrderBook (After Cancel)"
curl -s "$BASE_URL/orderbook?symbolId=1" | python3 -m json.tool
