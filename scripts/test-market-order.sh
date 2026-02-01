#!/bin/bash

BASE_URL="http://localhost:8080"

echo "=================================================="
echo "Market Order Test (IOC Behavior)"
echo "User 100: Seller (Maker)"
echo "User 101: Buyer (Taker - Market Order)"
echo "=================================================="

# 0. 입금 (Deposit)
echo -e "\n[Step 0] Depositing Funds..."
# User 100 needs 20 BTC for 2 orders of 10 BTC each. Depositing 30 BTC.
curl -s -X POST "$BASE_URL/deposit" -d "100,1,3000000000" > /dev/null
curl -s -X POST "$BASE_URL/deposit" -d "101,2,10000000000" > /dev/null
echo " > Deposited Funds."
sleep 0.5

# 1. 매도 주문 (Limit Sell) - 호가 조성
echo -e "\n[Step 1] Placing Limit Sell Orders (Liquidity)..."
# User 100: Sell 10 BTC @ 50000
curl -s -X POST "$BASE_URL/order" -d "100,1,50000,1000000000,2,1" > /dev/null
echo " > Limit Sell 10 BTC @ 50000"

# User 100: Sell 10 BTC @ 51000
curl -s -X POST "$BASE_URL/order" -d "100,1,51000,1000000000,2,1" > /dev/null
echo " > Limit Sell 10 BTC @ 51000"

sleep 0.5

echo -e "\n[Step 2] OrderBook Before Market Buy"
curl -s "$BASE_URL/orderbook?symbolId=1" | python3 -m json.tool

# 2. 시장가 매수 (Market Buy)
echo -e "\n[Step 3] Placing Market Buy Order..."
# User 101: Buy 15 BTC (Market) -> Should sweep 10 @ 50000, 5 @ 51000
# Price=0, Qty=1500000000, Side=1, Type=2(Market)
RESPONSE=$(curl -s -X POST "$BASE_URL/order" -d "101,1,0,1500000000,1,2")
echo " > Market Buy 15 BTC Sent: $RESPONSE"

sleep 0.5

# 3. 결과 확인
echo -e "\n[Step 4] OrderBook After Market Buy"
curl -s "$BASE_URL/orderbook?symbolId=1" | python3 -m json.tool
echo " > Expected: 5 BTC remaining at 51000"

echo -e "\n=================================================="
echo "Check 'me.log' for trades at 50000 and 51000."
echo "=================================================="