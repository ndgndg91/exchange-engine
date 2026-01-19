#!/bin/bash

BASE_URL="http://localhost:8080"

echo "=================================================="
echo "Exchange Engine Test Scenario"
echo "User 100: BTC Seller (Depositing Currency 1)"
echo "User 101: KRW Buyer  (Depositing Currency 2)"
echo "Symbol 1: BTC/KRW"
echo "=================================================="

# 1. 입금 (Deposit)
echo -e "\n[Step 1] Initial Deposits..."

# User 100에게 BTC(ID:1) 충분히 입금 (예: 1000 BTC)
# Format: userId,currencyId,amount
echo " > Depositing 1000 BTC to User 100"
curl -s -X POST "$BASE_URL/deposit" -d "100,1,1000"
echo ""

# User 101에게 KRW(ID:2) 충분히 입금 (예: 10억 KRW)
# 5 orders * 50,000 price = 250,000 required
echo " > Depositing 1,000,000,000 KRW to User 101"
curl -s -X POST "$BASE_URL/deposit" -d "101,2,1000000000"
echo ""

sleep 1

# 2. 매도 주문 5개 (User 100) - 오더북에 쌓임 (Maker)
echo -e "\n[Step 2] User 100 places 5 SELL Orders (Maker)..."
for i in {1..5}
do
   # Format: userId,currencyId,amount
   # Price: 50000, Qty: 1
   echo " > [Order #$i] User 100 Selling 1 BTC @ 50,000"
   curl -s -X POST "$BASE_URL/order" -d "100,1,50000,1,2"
   echo ""
   sleep 0.1
done

echo -e "\nWaiting for orders to rest in book..."
sleep 1

# 3. 매수 주문 5개 (User 101) - 즉시 체결 (Taker)
echo -e "\n[Step 3] User 101 places 5 BUY Orders (Taker)..."
for i in {1..5}
do
   # Price: 50000 (Matches Sell Order), Qty: 1
   echo " > [Order #$i] User 101 Buying 1 BTC @ 50,000"
   curl -s -X POST "$BASE_URL/order" -d "101,1,50000,1,1"
   echo ""
   sleep 0.1
done

echo -e "\n=================================================="
echo "Test Finished."
echo "Check 'me.log' for ExecutionReports and 'ome.log' for Balance updates."
echo "=================================================="
