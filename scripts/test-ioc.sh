#!/bin/bash

BASE_URL="http://localhost:8080"

echo "=================================================="
echo "IOC Order Test"
echo "User 100: Seller (Maker)"
echo "User 101: Buyer (IOC Taker)"
echo "=================================================="

# 0. 입금
curl -s -X POST "$BASE_URL/deposit" -d "100,1,1000000000" > /dev/null
# User 101 needs 100 Trillion KRW (50,000 * 2,000,000,000).
curl -s -X POST "$BASE_URL/deposit" -d "101,2,100000000000000" > /dev/null
sleep 0.5

# 1. 매도 주문 (10 BTC @ 50000)
curl -s -X POST "$BASE_URL/order" -d "100,1,50000,1000000000,2,1" > /dev/null
echo " > Limit Sell 10 BTC @ 50000 placed."
sleep 0.5

# 2. IOC 매수 주문 (20 BTC @ 50000)
# TIF=1 (IOC)
echo " > Placing Buy 20 BTC @ 50000 (IOC)..."
# userId,symbolId,price,qty,side,type,triggerPrice,tif
# type=1(Limit), trigger=0, tif=1(IOC)
RESPONSE=$(curl -s -X POST "$BASE_URL/order" -d "101,1,50000,2000000000,1,1,0,1")
echo " > Response: $RESPONSE"

sleep 0.5

# 3. 오더북 확인
echo -e "\n[Step 3] Checking OrderBook..."
curl -s "$BASE_URL/orderbook?symbolId=1" | python3 -m json.tool
echo " > Expected: Empty Bids (Remaining 10 BTC should be cancelled)"
echo " > Expected: Empty Asks (10 BTC should be filled)"
