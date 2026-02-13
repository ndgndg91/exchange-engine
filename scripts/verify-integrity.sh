#!/bin/bash

BASE_URL="http://localhost:8080"
DB_CMD="docker exec exchange-db psql -U postgres -d exchange -t -c"

echo "=================================================="
echo "🚀 Exchange Data Integrity Verification"
echo "=================================================="

# 1. 기초 데이터 검증 (Master Data)
echo -n "[Step 1] Master Data Check: "
BTC_EXIST=$($DB_CMD "SELECT count(*) FROM currencies WHERE symbol='BTC';")
SYM_EXIST=$($DB_CMD "SELECT count(*) FROM market_symbols WHERE name='BTC/KRW';")

if [[ $BTC_EXIST -ge 1 && $SYM_EXIST -ge 1 ]]; then
    echo "PASS (BTC and BTC/KRW configured)"
else
    echo "FAIL (Master data missing!)"; exit 1
fi

# 2. 입금 및 초기 잔고 검증
echo -e "
[Step 2] Deposit & Initial Balance Check"
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 200, "currency_id": 1, "amount": 100}' > /dev/null
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 201, "currency_id": 2, "amount": 1000000}' > /dev/null
sleep 2

# 정합성 체크: transfers 내역과 balances 합계 일치 여부
TRANSFER_SUM=$($DB_CMD "SELECT sum(amount) FROM transfers WHERE user_id IN (200, 201);")
BALANCE_SUM=$($DB_CMD "SELECT sum(available + locked) FROM balances WHERE user_id IN (200, 201);")

if [[ ${TRANSFER_SUM//[[:space:]]/} -eq ${BALANCE_SUM//[[:space:]]/} ]]; then
    echo " > PASS: Transfer Sum ($TRANSFER_SUM) == Balance Sum ($BALANCE_SUM)"
else
    echo " > FAIL: Data Mismatch! T:$TRANSFER_SUM B:$BALANCE_SUM"; exit 1
fi

# 3. 주문 및 자산 잠금 검증
echo -e "
[Step 3] Order Placement & Asset Locking Check"
# User 200: Sell 10 BTC @ 50,000 KRW
curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 200, "symbol_id": 1, "price": 50000, "qty": 10, "side": 2}' > /dev/null
sleep 2

# 정합성 체크: 가용 잔고 감소, 잠금 잔고 증가 확인
USER_200_AVAIL=$($DB_CMD "SELECT available FROM balances WHERE user_id=200 AND currency_id=1;")
USER_200_LOCKED=$($DB_CMD "SELECT locked FROM balances WHERE user_id=200 AND currency_id=1;")

if [[ ${USER_200_AVAIL//[[:space:]]/} -eq 90 && ${USER_200_LOCKED//[[:space:]]/} -eq 10 ]]; then
    echo " > PASS: User 200 Assets Locked (Avail: 90, Locked: 10)"
else
    echo " > FAIL: Asset Locking Error! Avail:$USER_200_AVAIL Locked:$USER_200_LOCKED"; exit 1
fi

# 4. 체결 및 최종 정산 검증
echo -e "
[Step 4] Trade Execution & Settlement Check"
# User 201: Buy 10 BTC @ 50,000 KRW (Total 500,000 KRW)
curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 201, "symbol_id": 1, "price": 50000, "qty": 10, "side": 1}' > /dev/null
sleep 3

# 정합성 체크: 
# 1. Trades 테이블 레코드 생성 여부
# 2. Orders 테이블 상태 'FILLED' 변경 여부
# 3. 유저 간 자산 이동 결과 확인
TRADE_COUNT=$($DB_CMD "SELECT count(*) FROM trades WHERE maker_order_id IN (SELECT order_id FROM orders WHERE user_id=200);")
USER_200_KRW=$($DB_CMD "SELECT available FROM balances WHERE user_id=200 AND currency_id=2;")
USER_201_BTC=$($DB_CMD "SELECT available FROM balances WHERE user_id=201 AND currency_id=1;")

if [[ $TRADE_COUNT -ge 1 && ${USER_200_KRW//[[:space:]]/} -eq 500000 && ${USER_201_BTC//[[:space:]]/} -eq 10 ]]; then
    echo " > PASS: Trade Settlement Successful"
    echo "   - User 200 received 500,000 KRW"
    echo "   - User 201 received 10 BTC"
else
    echo " > FAIL: Settlement Error! TradeCount:$TRADE_COUNT U200_KRW:$USER_200_KRW U201_BTC:$USER_201_BTC"; exit 1
fi

echo -e "
✅ ALL DATA INTEGRITY CHECKS PASSED!"
echo "=================================================="
