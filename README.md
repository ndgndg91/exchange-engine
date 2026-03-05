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

## Quick Start

### 1. Build
```bash
# JVM
./gradlew :jvm:shadowJar

# Rust
cd rust && cargo build --release
```

### 2. Run Locally (Bare Metal)
```bash
# JVM version
./run-local.sh

# Rust version
./run-local-rust.sh
```

### 3. Run with Docker Compose
```bash
# JVM version
docker compose -f deploy/local/docker-compose.jvm.yml up --build

# Rust version
docker compose -f deploy/local/docker-compose.rust.yml up --build
```

### 4. Deploy to Kubernetes
```bash
# Rust
docker build -f deploy/docker/Dockerfile.rust -t exchange-engine-rust:latest .
kubectl apply -f deploy/k8s/base/db.yaml
kubectl apply -f deploy/k8s/rust/apps.yaml

# JVM
docker build -f deploy/docker/Dockerfile.jvm -t exchange-engine-jvm:latest .
kubectl apply -f deploy/k8s/base/db.yaml
kubectl apply -f deploy/k8s/jvm/services.yaml
kubectl apply -f deploy/k8s/jvm/apps.yaml
```

### 5. Verify Integrity
```bash
./scripts/verify-integrity.sh
```

---

For detailed deployment instructions, see [DEPLOYMENT.md](DEPLOYMENT.md).
