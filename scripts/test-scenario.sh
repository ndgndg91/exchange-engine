#!/bin/bash

BASE_URL="http://127.0.0.1:8080"

echo "=================================================="
echo "Exchange Engine Test Scenario (JSON)"
echo "User 100: BTC Seller, User 101: KRW Buyer"
echo "=================================================="

# 1. 입금
echo -e "\n[Step 1] Initial Deposits..."
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 100, "currency_id": 1, "amount": 1000}' > /dev/null
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 101, "currency_id": 2, "amount": 1000000000}' > /dev/null
echo " > Deposited 1000 BTC to U100 and 1B KRW to U101"

sleep 1

# 2. 매도 주문 5개 (User 100) - Maker
echo -e "\n[Step 2] User 100 places 5 SELL Orders (Maker)..."
for i in {1..5}
do
   curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 100, "symbol_id": 1, "price": 50000, "qty": 1, "side": 2}' > /dev/null
   echo " > [Order #$i] User 100 Selling 1 BTC @ 50,000"
   sleep 0.1
done

sleep 1

# 3. 매수 주문 5개 (User 101) - Taker
echo -e "\n[Step 3] User 101 places 5 BUY Orders (Taker)..."
for i in {1..5}
do
   curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 101, "symbol_id": 1, "price": 50000, "qty": 1, "side": 1}' > /dev/null
   echo " > [Order #$i] User 101 Buying 1 BTC @ 50,000"
   sleep 0.1
done

echo -e "\n=================================================="
echo "Basic Test Finished. Check logs for matches."
echo "=================================================="
