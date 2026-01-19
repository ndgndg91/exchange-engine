#!/bin/bash

BASE_URL="http://localhost:8080"

echo "=================================================="
echo "OrderBook API Test"
echo "Symbol 1: BTC/KRW"
echo "=================================================="

# 0. 사전 입금 (Deposit) - Risk Check 통과를 위해 필수
echo -e "\n[Step 0] Depositing Funds..."
# User 100: Sell BTC -> Needs BTC (Currency 1)
curl -s -X POST "$BASE_URL/deposit" -d "100,1,1000000000" > /dev/null
echo " > Deposited 10 BTC to User 100"

# User 101: Buy BTC -> Needs KRW (Currency 2)
curl -s -X POST "$BASE_URL/deposit" -d "101,2,10000000000" > /dev/null
echo " > Deposited 10,000,000 KRW to User 101"

sleep 1

# 1. 초기화 (주문 넣기)
echo -e "\n[Step 1] Placing Maker Orders..."

# User 100: Sell 1 BTC @ 50100
curl -s -X POST "$BASE_URL/order" -d "100,1,50100,100000000,2" > /dev/null
echo " > Sell 1 BTC @ 50100"
sleep 0.1

# User 100: Sell 0.5 BTC @ 50000
curl -s -X POST "$BASE_URL/order" -d "100,1,50000,50000000,2" > /dev/null
echo " > Sell 0.5 BTC @ 50000"
sleep 0.1

# User 101: Buy 2 BTC @ 49000
curl -s -X POST "$BASE_URL/order" -d "101,1,49000,200000000,1" > /dev/null
echo " > Buy 2 BTC @ 49000"
sleep 0.5

# 2. 오더북 조회
echo -e "\n[Step 2] Querying OrderBook..."
echo "GET $BASE_URL/orderbook?symbolId=1"
echo "--------------------------------------------------"
curl -s "$BASE_URL/orderbook?symbolId=1" | python3 -m json.tool
echo -e "\n--------------------------------------------------"

echo "Expected:"
echo "Asks: [50000, 50000000], [50100, 100000000]"
echo "Bids: [49000, 200000000]"
