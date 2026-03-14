#!/bin/bash

BASE_URL="http://localhost:8080"
# Default to docker if not provided
if [[ -z "$DB_CMD" ]]; then
    DB_CMD="docker exec exchange-db psql -U postgres -d exchange -t -c"
fi

# 0. Clean state for the test
$DB_CMD "TRUNCATE balances, orders, trades, transfers;" > /dev/null 2>&1 || true

echo "=================================================="
echo "🚀 Exchange Data Integrity Verification"
echo "=================================================="

# 1. 기초 데이터 검증 (Master Data)
echo -e "[Step 1] Master Data Check"
SYMBOL_COUNT=$($DB_CMD "SELECT count(*) FROM market_symbols WHERE symbol_id=1;")
CURRENCY_COUNT=$($DB_CMD "SELECT count(*) FROM currencies WHERE currency_id IN (1, 2);")

if [[ $SYMBOL_COUNT -ge 1 && $CURRENCY_COUNT -ge 2 ]]; then
    echo " > PASS (BTC and BTC/KRW configured)"
else
    echo " > FAIL (Master data missing!)"; exit 1
fi

# 2. 입금 및 초기 잔고 검증
echo -e "
[Step 2] Deposit & Initial Balance Check"
# User 200: Deposit 2 BTC (200,000,000 sats)
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 200, "currency_id": 1, "amount": 200000000}' > /dev/null
# User 201: Deposit 100,000,000 KRW
curl -s -X POST "$BASE_URL/deposit" -H "Content-Type: application/json" -d '{"user_id": 201, "currency_id": 2, "amount": 100000000}' > /dev/null
sleep 2

# 정합성 체크
TRANSFER_SUM=$($DB_CMD "SELECT sum(amount) FROM transfers WHERE user_id IN (200, 201);")
BALANCE_SUM=$($DB_CMD "SELECT sum(available + locked) FROM balances WHERE user_id IN (200, 201);")

if [[ ${TRANSFER_SUM//[[:space:]]/} -eq ${BALANCE_SUM//[[:space:]]/} ]]; then
    echo " > PASS: Transfer Sum ($TRANSFER_SUM) == Balance Sum ($BALANCE_SUM)"
else
    echo " > FAIL: Data Mismatch! T:$TRANSFER_SUM B:$BALANCE_SUM"; exit 1
fi

# 2-1. Withdrawal Check
echo -e "
[Step 2-1] Withdrawal Check"
# User 201: Withdraw 100,000 KRW
curl -s -X POST "$BASE_URL/withdraw" -H "Content-Type: application/json" -d '{"user_id": 201, "currency_id": 2, "amount": 100000, "seq_id": 999}' > /dev/null
sleep 2

USER_201_BAL_AFTER=$($DB_CMD "SELECT available + locked FROM balances WHERE user_id=201 AND currency_id=2;")
if [[ ${USER_201_BAL_AFTER//[[:space:]]/} -eq 99900000 ]]; then
    echo " > PASS: User 201 Withdrawal Successful (Remaining: 99,900,000)"
else
    echo " > FAIL: Withdrawal Error! Balance:$USER_201_BAL_AFTER"; exit 1
fi

# 3. 주문 및 자산 잠금 검증
echo -e "
[Step 3] Order Placement & Asset Locking Check"
# User 200: Sell 1.0 BTC (100,000,000 sats) @ 50,000 KRW
curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 200, "symbol_id": 1, "price": 50000, "qty": 100000000, "side": 2}' > /dev/null
sleep 2

# 정합성 체크: 가용 잔고 감소, 잠금 잔고 증가 확인
USER_200_AVAIL=$($DB_CMD "SELECT available FROM balances WHERE user_id=200 AND currency_id=1;")
USER_200_LOCKED=$($DB_CMD "SELECT locked FROM balances WHERE user_id=200 AND currency_id=1;")

if [[ ${USER_200_AVAIL//[[:space:]]/} -eq 100000000 && ${USER_200_LOCKED//[[:space:]]/} -eq 100000000 ]]; then
    echo " > PASS: User 200 Assets Locked (Avail: 100M, Locked: 100M)"
else
    echo " > FAIL: Asset Locking Error! Avail:$USER_200_AVAIL Locked:$USER_200_LOCKED"; exit 1
fi

# 4. 체결 및 최종 정산 검증
echo -e "
[Step 4] Trade Execution & Settlement Check"
# User 201: Buy 1.0 BTC @ 50,000 KRW (Total 50,000,000 KRW cost)
# User 201 has 99,900,000 KRW available. 50,000,000 cost is fine.
curl -s -X POST "$BASE_URL/order" -H "Content-Type: application/json" -d '{"user_id": 201, "symbol_id": 1, "price": 50000, "qty": 100000000, "side": 1}' > /dev/null
sleep 3

# 정합성 체크
TRADE_COUNT=$($DB_CMD "SELECT count(*) FROM trades WHERE maker_order_id IN (SELECT order_id FROM orders WHERE user_id=200);")
USER_200_KRW=$($DB_CMD "SELECT available FROM balances WHERE user_id=200 AND currency_id=2;")
USER_201_BTC=$($DB_CMD "SELECT available FROM balances WHERE user_id=201 AND currency_id=1;")

# User 200 (Maker Sell): Gained 50,000,000 KRW
# User 201 (Taker Buy): Gained 1.0 BTC (100,000,000 sats)
if [[ $TRADE_COUNT -ge 1 && ${USER_200_KRW//[[:space:]]/} -eq 50000 && ${USER_201_BTC//[[:space:]]/} -eq 100000000 ]]; then
    echo " > PASS: Trade Settlement Successful"
else
    echo " > FAIL: Settlement Error! TradeCount:$TRADE_COUNT U200_KRW:$USER_200_KRW U201_BTC:$USER_201_BTC"; exit 1
fi

echo -e "
✅ ALL DATA INTEGRITY CHECKS PASSED!"
echo "=================================================="
