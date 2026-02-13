# Exchange Engine (Polyglot High-Performance Matching Engine)

> **Extreme Low Latency (< 100µs) & High Throughput (100k+ TPS)**  
> Built with **Kotlin (JVM)** and **Rust**, sharing a unified protocol via SBE.

## 🚀 Overview

This project implements a next-generation cryptocurrency exchange matching engine designed for high-frequency trading (HFT). It features a **Polyglot Architecture**, providing two identical implementations: one in JVM (Kotlin) and one in Rust, allowing for direct performance comparison and interoperability.

### Key Principles
*   **In-Memory Computing:** All matching and risk checks happen in RAM. Disk I/O is removed from the critical path.
*   **Zero-GC (JVM) / Memory Safety (Rust):** Optimized for zero-pause execution.
*   **Scale Factor Precision:** Handles BTC (8 decimals) and KRW (0 decimals) with integer math: `(price * qty) / 10^8`.
*   **Data Integrity:** Guaranteed asset conservation across `balances`, `orders`, and `trades` tables.

---

## 🏗 Polyglot Architecture

The system supports two execution modes. Both share the same SBE schema and PostgreSQL schema.

### Directory Structure
*   `jvm/`: Kotlin implementation using Aeron IPC and LMAX Disruptor.
*   `rust/`: Rust implementation using Lock-free channels and Axum.
*   `shared/`: Common resources like `exchange-schema.xml`.
*   `scripts/`: Testing, simulation, and integrity verification tools.

---

## 🛠 Tech Stack

| Component | JVM Implementation | Rust Implementation |
| :--- | :--- | :--- |
| **Language** | Kotlin (JDK 21) | Rust (Edition 2021) |
| **Messaging** | Aeron IPC (Binary) | TCP Stream (JSONL) |
| **Concurrency** | LMAX Disruptor | Crossbeam / Tokio |
| **API Server** | Netty / Jackson | Axum / Serde |
| **Database** | JDBC / HikariCP | SQLx (Async) |

---

## 📚 API Reference (Port 8080)

All endpoints now accept **JSON** payloads.

| Method | Endpoint | Payload Example | Description |
|:---|:---|:---|:---|
| `POST` | `/deposit` | `{"user_id": 101, "currency_id": 1, "amount": 100000000}` | Deposit funds (BTC Scale 10^8) |
| `POST` | `/order` | `{"user_id": 101, "symbol_id": 1, "price": 50000, "qty": 100000000, "side": 1}` | Place Order (1=Buy, 2=Sell) |
| `GET` | `/orderbook` | `?symbol_id=1` | Get OrderBook L2 Snapshot |

---

## ⚡ Quick Start

### 1. Build Both Versions
```bash
# JVM
./gradlew :jvm:shadowJar

# Rust
cd rust && cargo build --release
```

### 2. Run Locally
```bash
# To run JVM version
./run-local.sh

# To run Rust version
./run-local-rust.sh
```

### 3. Verify Integrity
```bash
./scripts/verify-integrity.sh
```
