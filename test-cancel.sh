#!/bin/bash

BASE_URL="http://localhost:8080"

echo "=================================================="
echo "Order Cancel Test"
echo "Scenario: Place Order -> Check Book -> Cancel -> Check Book"
echo "=================================================="

# 0. 입금 (Deposit)
echo -e "\n[Step 0] Depositing Funds..."
curl -s -X POST "$BASE_URL/deposit" -d "100,1,1000000000" > /dev/null
echo " > Deposited 10 BTC to User 100"
sleep 0.5

# 1. 주문 생성
echo -e "\n[Step 1] Placing Maker Order..."
# User 100: Sell 1 BTC @ 50000
RESPONSE=$(curl -s -X POST "$BASE_URL/order" -d "100,1,50000,100000000,2")
echo " > Response: $RESPONSE"

# Extract OrderID (SeqId) from response "Order Sent: 12345"
ORDER_ID=$(echo $RESPONSE | awk -F': ' '{print $2}')
echo " > Extracted Order ID: $ORDER_ID"

sleep 0.5

# 2. 오더북 확인 (Before Cancel)
echo -e "\n[Step 2] OrderBook (Before Cancel)"
curl -s "$BASE_URL/orderbook?symbolId=1" | python3 -m json.tool
echo " > Expected: One ask at 50000"

sleep 1

# 3. 주문 취소
echo -e "\n[Step 3] Cancelling Order #$ORDER_ID..."
# /cancel -> userId,orderId,symbolId
CANCEL_RES=$(curl -s -X POST "$BASE_URL/cancel" -d "100,$ORDER_ID,1")
echo " > Cancel Response: $CANCEL_RES"

sleep 0.5

# 4. 오더북 확인 (After Cancel)
echo -e "\n[Step 4] OrderBook (After Cancel)"
curl -s "$BASE_URL/orderbook?symbolId=1" | python3 -m json.tool
echo " > Expected: Empty Asks"

echo -e "\n=================================================="
echo "Check 'ome.log' for 'Refunded' message."
echo "=================================================="
