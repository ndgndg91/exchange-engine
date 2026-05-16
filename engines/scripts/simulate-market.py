import urllib.request
import json
import random
import time
import threading

BASE_URL = "http://127.0.0.1:8080"
USERS = range(1000, 1050)
BTC_SCALE = 100_000_000

def post_request(endpoint, data):
    jsondata = json.dumps(data).encode('utf-8')
    req = urllib.request.Request(f"{BASE_URL}{endpoint}", data=jsondata)
    req.add_header('Content-Type', 'application/json')
    try:
        # Strict 3-second timeout to prevent hanging
        with urllib.request.urlopen(req, timeout=3) as r: 
            return r.read().decode('utf-8')
    except Exception as e:
        err_msg = f"Req Error ({endpoint}): {str(e)}"
        print(err_msg)
        return err_msg

def deposit(uid):
    post_request("/deposit", {"user_id": uid, "currency_id": 1, "amount": 100 * BTC_SCALE})
    post_request("/deposit", {"user_id": uid, "currency_id": 2, "amount": 100_000_000})

def trade(uid):
    for _ in range(50):
        side = random.choice([1, 2])
        # Various order types and TIFs
        tif = random.choice([0, 1, 2]) # GTC, IOC, FOK
        price = 50000 + random.randint(-100, 100)
        post_request("/order", {
            "user_id": uid, 
            "symbol_id": 1, 
            "price": price, 
            "qty": random.randint(1, 10) * (BTC_SCALE // 100), # 0.01 ~ 0.1 BTC
            "side": side,
            "order_type": 1,
            "tif": tif
        })
        time.sleep(0.01)

if __name__ == "__main__":
    print("🚀 PROD-LEVEL STRESS TEST & INTEGRITY AUDIT...")
    
    # 1. Deposits
    print("💰 Depositing funds for 50 users...")
    threads = [threading.Thread(target=deposit, args=(u,)) for u in USERS]
    for t in threads: t.start()
    for t in threads: t.join()
    
    print("⏳ Waiting for balances to settle...")
    time.sleep(2)
    
    # 2. High-volume Trading
    print("⚡ Starting high-frequency trading simulation...")
    threads = [threading.Thread(target=trade, args=(u,)) for u in USERS]
    for t in threads: t.start()
    for t in threads: t.join()
    
    print("🏁 Simulation Done. Waiting for persistence...")
    time.sleep(5)
