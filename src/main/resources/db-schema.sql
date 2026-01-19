-- Users & Balances
CREATE TABLE IF NOT EXISTS balances (
    user_id BIGINT NOT NULL,
    currency_id INT NOT NULL,
    available BIGINT NOT NULL DEFAULT 0,
    locked BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, currency_id)
);

-- Orders
CREATE TABLE IF NOT EXISTS orders (
    order_id BIGINT PRIMARY KEY, -- Global Sequence ID (seqId) or OME-assigned ID
    user_id BIGINT NOT NULL,
    symbol_id INT NOT NULL,
    price BIGINT NOT NULL,
    qty BIGINT NOT NULL,
    side INT NOT NULL, -- 1=Buy, 2=Sell
    status VARCHAR(20) NOT NULL, -- NEW, FILLED, PARTIALLY_FILLED, CANCELED, CANCELLED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Trades (Executions)
CREATE TABLE IF NOT EXISTS trades (
    match_id BIGINT PRIMARY KEY,
    maker_order_id BIGINT NOT NULL,
    taker_order_id BIGINT NOT NULL,
    price BIGINT NOT NULL,
    qty BIGINT NOT NULL,
    side INT NOT NULL, -- Taker Side
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Transfers (Deposit/Withdraw)
CREATE TABLE IF NOT EXISTS transfers (
    seq_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    currency_id INT NOT NULL,
    amount BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL, -- DEPOSIT, WITHDRAW
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Currencies (Master Data)
CREATE TABLE IF NOT EXISTS currencies (
    currency_id INT PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    scale INT NOT NULL DEFAULT 8
);

-- Market Symbols (Trading Pairs)
CREATE TABLE IF NOT EXISTS market_symbols (
    symbol_id INT PRIMARY KEY,
    name VARCHAR(20) NOT NULL, -- BTC/KRW
    base_currency_id INT NOT NULL, -- BTC (1)
    quote_currency_id INT NOT NULL, -- KRW (2)
    price_scale INT NOT NULL DEFAULT 0, -- Price Precision
    qty_scale INT NOT NULL DEFAULT 8 -- Quantity Precision (usually same as Base Currency)
);

-- Initial Data
INSERT INTO currencies (currency_id, symbol, scale) VALUES (1, 'BTC', 8) ON CONFLICT DO NOTHING;
INSERT INTO currencies (currency_id, symbol, scale) VALUES (2, 'KRW', 0) ON CONFLICT DO NOTHING;
INSERT INTO currencies (currency_id, symbol, scale) VALUES (3, 'USDT', 2) ON CONFLICT DO NOTHING;

INSERT INTO market_symbols (symbol_id, name, base_currency_id, quote_currency_id, price_scale, qty_scale) 
VALUES (1, 'BTC/KRW', 1, 2, 0, 8) ON CONFLICT DO NOTHING;