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
        with urllib.request.urlopen(req, timeout=5) as r: return r.read()
    except: pass

def deposit(uid):
    post_request("/deposit", {"user_id": uid, "currency_id": 1, "amount": 100 * BTC_SCALE})
    post_request("/deposit", {"user_id": uid, "currency_id": 2, "amount": 100_000_000})

def trade(uid):
    for _ in range(50):
        # ALL ORDERS AT SAME PRICE = IMMEDIATE MATCHES
        post_request("/order", {"user_id": uid, "symbol_id": 1, "price": 50000, "qty": BTC_SCALE, "side": random.choice([1, 2])})
        time.sleep(0.01)

if __name__ == "__main__":
    print("🚀 MATCHING FORCE SIMULATION...")
    [t.start() for t in [threading.Thread(target=deposit, args=(u,)) for u in USERS]]
    print("💰 Deposits done.")
    time.sleep(2)
    threads = [threading.Thread(target=trade, args=(u,)) for u in USERS]
    for t in threads: t.start()
    for t in threads: t.join()
    print("🏁 Done.")
