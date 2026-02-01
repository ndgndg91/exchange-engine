#!/bin/bash

BASE_URL="http://localhost:8080"

echo "=================================================="
echo "Stop Order Test (Stop-Market)"
echo "User 100: Stop-Loss Seller"
echo "User 101: Price Triggerer"
echo "=================================================="

# 0. 입금 (Deposit)
echo -e "\n[Step 0] Depositing Funds..."
curl -s -X POST "$BASE_URL/deposit" -d "100,1,1000000000" > /dev/null
# User 101 needs 5 Trillion KRW (100,000,000 * 50,000). Depositing 10 Trillion.
curl -s -X POST "$BASE_URL/deposit" -d "101,2,10000000000000" > /dev/null
echo " > Deposited Funds."
sleep 0.5

# 1. 유동성 공급 (Buy Maker) - 50,000원에 매수 대기열 만들기
echo -e "\n[Step 1] Placing Buy Maker at 50,000..."
curl -s -X POST "$BASE_URL/order" -d "101,1,50000,100000000,1,1" > /dev/null
sleep 0.1

# 2. 스탑 주문 등록 (Stop-Market Sell)
# "Trigger at 50,000 or below -> Market Sell"
echo -e "\n[Step 2] Placing Stop-Market Sell (Trigger <= 50,000)..."
# format: userId,symbolId,price,qty,side,type,triggerPrice
# price=0 (market), qty=100000000 (1 BTC), side=2 (sell), type=4 (StopMarket), trigger=50000
curl -s -X POST "$BASE_URL/order" -d "100,1,0,100000000,2,4,50000" > /dev/null
echo " > Stop Order Registered."

sleep 0.5

# 3. 트리거 발생 시키기 (매도 주문을 넣어 50,000원에 체결 발생)
echo -e "\n[Step 3] Triggering Stop by making a trade at 50,000..."
# User 100: Sell 1 BTC @ 50,000 (Hits the Maker User 101 at 50,000)
# Use User 100 to avoid Self-Trade Protection (since Maker is 101)
curl -s -X POST "$BASE_URL/order" -d "100,1,50000,100000000,2,1" > /dev/null
echo " > Trade happened at 50,000."

sleep 1

echo -e "\n[Step 4] Checking Logs..."
echo "--------------------------------------------------"
echo "Check 'me.log' for: 'STOP TRIGGERED! Order #... '"
echo "Check 'me.log' for: 'MATCH' resulting from the stop order."
echo "--------------------------------------------------"
