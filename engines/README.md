# Exchange Engine (Polyglot High-Performance Matching Engine)

> **Extreme Low Latency (< 100us) & High Throughput (100k+ TPS)**
> Built with **Kotlin (JVM)** and **Rust**, sharing a unified protocol via SBE.

## Overview

This project implements a next-generation cryptocurrency exchange matching engine designed for high-frequency trading (HFT). It features a **Polyglot Architecture**, providing two identical implementations: one in JVM (Kotlin) and one in Rust, allowing for direct performance comparison and interoperability.

### Key Principles
*   **In-Memory Computing:** All matching and risk checks happen in RAM. Disk I/O is removed from the critical path.
*   **Zero-GC (JVM) / Memory Safety (Rust):** Optimized for zero-pause execution.
*   **Scale Factor Precision:** Handles BTC (8 decimals) and KRW (0 decimals) with integer math: `(price * qty) / 10^8`.
*   **Data Integrity:** Guaranteed asset conservation across `balances`, `orders`, and `trades` tables.

---

## Polyglot Architecture

The system supports two execution modes. Both share the same SBE schema and PostgreSQL schema.

### Directory Structure
```
jvm/                  Kotlin implementation (Aeron IPC, LMAX Disruptor)
rust/                 Rust implementation (TCP JSON, Crossbeam, Axum)
shared/               Common resources (exchange-schema.xml)
scripts/              Testing, simulation, and integrity verification tools
deploy/
  docker/             Dockerfiles (JVM, Rust)
  local/              docker-compose files (local development)
  k8s/
    base/             Shared K8s resources (DB)
    jvm/              JVM K8s manifests (Aeron UDP)
    rust/             Rust K8s manifests (TCP JSON)
```

---

## Tech Stack

| Component | JVM Implementation | Rust Implementation |
| :--- | :--- | :--- |
| **Language** | Kotlin (JDK 21) | Rust (Edition 2021) |
| **Messaging** | Aeron IPC (Binary) | TCP Stream (JSONL) |
| **Concurrency** | LMAX Disruptor | Crossbeam / Tokio |
| **API Server** | Netty / Jackson | Axum / Serde |
| **Database** | JDBC / HikariCP | SQLx (Async) |
| **Order Types** | Limit, Market, StopLimit, StopMarket | Limit, Market, StopLimit, StopMarket |
| **TIF** | GTC, IOC, FOK | GTC, IOC, FOK |
| **Event Journal** | Chronicle Queue (WAL) | File-based JSONL (WAL) |
| **Idempotency** | lastProcessedSeqId | last_processed_seq_id |
| **OrderBook Snapshot** | Aeron broadcast | TCP broadcast (port 5559) |

---

## API Reference (Port 8080)

All endpoints accept **JSON** payloads.

| Method | Endpoint | Payload Example | Description |
|:---|:---|:---|:---|
| `POST` | `/deposit` | `{"user_id": 101, "currency_id": 1, "amount": 100000000}` | Deposit funds (BTC Scale 10^8) |
| `POST` | `/order` | `{"user_id": 101, "symbol_id": 1, "price": 50000, "qty": 100000000, "side": 1}` | Place Order (1=Buy, 2=Sell) |
| `POST` | `/cancel` | `{"user_id": 101, "order_id": 1, "symbol_id": 1}` | Cancel Order |
| `GET` | `/orderbook` | `?symbol_id=1` | Get OrderBook L2 Snapshot |

### Order Options
| Field | Values | Description |
|:---|:---|:---|
| `order_type` | 1=Limit, 2=Market, 3=StopLimit, 4=StopMarket | Default: Limit |
| `tif` | 0=GTC, 1=IOC, 2=FOK | Default: GTC |
| `trigger_price` | integer | Stop order trigger price |

---

## Quick Start (Standard Testing Flow)

The easiest way to build, deploy, and verify the entire system is using the standardized scripts in the `scripts/` directory.

### 1. Unified K8s Testing (Recommended)
This automatically handles building, Kind cluster setup, deployment, and comprehensive test execution.

```bash
# For JVM (Kotlin) Stack
./scripts/env-k8s.sh up-jvm
./scripts/run-tests.sh

# For Rust Stack
./scripts/env-k8s.sh up-rust
./scripts/run-tests.sh

# Cleanup
./scripts/env-k8s.sh down
```

### 2. Individual Component Build
```bash
# JVM
./gradlew :jvm:shadowJar

# Rust
cd rust && cargo build --release
```

### 3. Verification & Simulation
After the stack is up (via `env-k8s.sh`), you can run individual tools:
```bash
# Run all scenario tests + high-volume simulation + integrity audit
./scripts/run-tests.sh

# Or run specific scenarios
./scripts/test-scenario.sh
./scripts/simulate-market.py
```

---

For detailed deployment instructions, see [DEPLOYMENT.md](DEPLOYMENT.md).
