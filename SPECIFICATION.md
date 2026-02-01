# 1. 개요 (Overview)

## 1.1 프로젝트 목표

본 프로젝트는 기존 RDB 중심의 트랜잭션 처리 한계를 극복하고, `Extreme Low Latency(< 100µs)`와 `High Throughput(100k+ TPS)`을 보장하는 메모리 기반의 차세대 암호화폐 거래소 매칭 엔진을 구축하는 것을 목표로 한다.

## 1.2 핵심 설계 원칙

1. **In-Memory Computing:** 모든 체결 및 잔고 처리는 메모리(RAM) 내에서 수행하며, 디스크 I/O 를 Critical Path 에서 배제한다.
2. **Zero-GC Architecture:** JVM 기반(Kotlin) 이나 Garbage Collection 으로 인한 Stop-the-world 를 방지하기 위해 Off-heap 메모리 및 Object Pooling 을 적극 도입한다.
3. **Concurrency Isolation:** 자산 관리(Risk)와 주문 체결(Matching)의 책임을 물리적으로 분리하여 Lock Contention을 제거한다.
4. **Deterministic Replay:** 이벤트 소싱(Event Sourcing) 을 통해 모든 상태는 입력 이벤트의 재현(Replay)으로 완벽하게 복구 가능해야 한다.

# 2. 시스템 아키텍처 (System Architecture)

시스템은 역할에 따라 크게 OME(Order Management Engine)와 ME(Matching Engine) 로 분리되며, 이들은 초고속 메세징 버스(Aeron)로 연결된다.

## 2.1 컴포넌트 구성

| **컴포넌트** | **역할** | **기술 스택** |
| --- | --- | --- |
| **Gateway** | 프로토콜 변환(HTTP/REST → Binary), 인증, API 서빙 | Netty (Custom TCP/HTTP) |
| **OME (Risk Engine)** | 사전 리스크 관리(Pre-trade check), 자산 잠금(Locking), 주문 라우팅, 잔고 복구(Refund) | Kotlin, Aeron |
| **ME (Matching Engine)** | 오더북 관리(Limit Order Book), 호가 매칭(Execution), 시세 생성 | Kotlin, LMAX Disruptor |
| **Persistence Worker** | 비동기 데이터 저장 (RDB: Orders, Trades, Balances) | Kotlin, JDBC (HikariCP) |

## 2.2 데이터 흐름 (Data Flow)

**1. 주문 흐름 (Order Flow)**
1.  **Ingest:** 유저 주문 요청(HTTP) → **Gateway**
2.  **Input Stream (ID: 10):** Gateway → **OME**
3.  **Risk Check:** **OME**가 잔고 확인 및 자산 잠금(`Locked`) 수행.
    - *Pass:* 주문을 승인하고 **Engine Stream (ID: 11)**으로 전송.
    - *Fail:* 주문을 즉시 거절하고 폐기.
4.  **Matching:** **ME**가 Engine Stream(11)을 구독하여 오더북 매칭 수행.
5.  **Execution:** 체결 발생 시 `ExecutionReport`를 **Event Stream (ID: 20)**으로 발행.
6.  **Settlement:** **OME**가 Event Stream(20)을 구독하여 체결 결과에 따라 잔고 정산 수행.

**2. 취소 흐름 (Cancel Flow)**
1.  **Request:** 취소 요청(HTTP) → **Gateway** → **OME** (Input Stream 10)
2.  **Routing:** **OME**는 취소 요청을 검증 없이 **ME** (Engine Stream 11)로 전달.
3.  **Cancellation:** **ME**는 오더북에서 주문(`orderId`)을 찾아 삭제.
    - 성공 시: `ExecutionReport (ExecType=Cancel)` 발행.
4.  **Refund:** **OME**는 취소 리포트를 수신하여 잠긴 자산을 즉시 **환불(Unlock)**.

**3. 마켓 데이터 흐름 (Market Data Flow)**
1.  **Snapshot Publishing:** **ME**는 오더북 변경 시 `OrderBookSnapshot` 메시지를 **Event Stream (ID: 20)**으로 멀티캐스트.
2.  **Local Replication:** **Gateway**는 이 메시지를 구독하여 로컬 메모리(`ConcurrentHashMap`)에 최신 오더북 상태를 동기화.
3.  **API Serving:** 클라이언트가 `GET /orderbook` 요청 시, Gateway는 로컬 메모리의 데이터를 즉시 반환 (Zero-Latency).

# 3. 기술 상세 사양 (Technical Specifications)

## 3.1 언어 및 런타임

- **Language:** Kotlin (JVM Target 21+)
- **Style:** Zero-Allocation Code Style (No Iterators, No Streams, Primitive Collections 사용)

## 3.2 통신 및 메시징(IPC)

- **Protocol:** Aeron (UDP Unicast/Multicast or IPC)
- **Serialization:** SBE (Simple Binary Encoding)
- **Stream IDs:**
    - `10`: Gateway -> OME (Commands: Order, Deposit, Cancel)
    - `11`: OME -> ME (Validated Orders, Cancel Requests)
    - `20`: ME -> OME/Gateway (Events: Execution, Market Data)

## 3.3 API 명세 (HTTP Gateway)

**Base URL:** `http://localhost:8080`

1.  **입금 (Deposit):** `POST /deposit`
    - Body: `userId,currencyId,amount` (CSV)
2.  **주문 (Order):** `POST /order`
    - Body: `userId,symbolId,price,qty,side` (CSV, Side: 1=Buy, 2=Sell)
3.  **주문 취소 (Cancel):** `POST /cancel`
    - Body: `userId,orderId,symbolId` (CSV)
4.  **오더북 조회 (OrderBook):** `GET /orderbook?symbolId=1`
    - Response: JSON format `{ "bids": [...], "asks": [...] }`

## 3.4 데이터 영속성 (Persistence)

- **Database:** PostgreSQL (Async Persistence via Worker)
    - `currencies`: 통화 정보 (BTC, KRW 등) 및 Scale.
    - `market_symbols`: 거래 쌍 정보 (BTC/KRW) 및 Price Scale.
    - `orders`: 주문 내역 (상태: NEW, FILLED, PARTIALLY_FILLED, CANCELLED).
    - `trades`: 체결 내역.
    - `balances`: 사용자 잔고 (Available, Locked).

# 5. 향후 개발 로드맵 (Future Roadmap)

## Phase 1: 안정화 및 기능 확장 (Completed)
- [x] OME Gatekeeper 아키텍처 (Risk Check)
- [x] 주문 취소 및 환불 (Refund) 로직
- [x] 오더북 스냅샷 API
- [x] DB 스키마 자동 초기화 및 Currency 관리

## Phase 2: 고급 주문 유형 (Completed)
- [x] **Market Orders:** 시장가 주문 지원.
- [x] **Stop Orders:** Stop-Limit 및 Stop-Market 주문 지원.
- [x] **Time-In-Force:** IOC (Immediate or Cancel) 및 GTC 지원.

## Phase 3: 실시간성 및 확장성 (Next Steps)
- [ ] **Redis Pub/Sub:** Gateway가 Aeron 이벤트를 Redis로 발행하여 WebSocket 서버와 분리.
- [ ] **WebSocket Server:** 실시간 호가창 및 체결 알림 Push 서버 구축.
- [ ] **Recovery:** 서버 재시작 시 Journal(Chronicle Queue) Replay를 통한 메모리 상태 복구.

## Phase 4: 상용화 준비 (Production)
- [ ] **Aeron Cluster:** Raft 합의 알고리즘을 통한 고가용성(HA) 구성.
- [ ] **Market Data Feed:** 별도의 Market Data Processor 구축 (Candle chart 생성 등).
- [ ] **Monitoring:** Prometheus/Grafana 연동 (HdrHistogram 활용).