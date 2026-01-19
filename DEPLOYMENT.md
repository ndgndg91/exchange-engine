# 배포 및 실행 가이드 (Deployment Guide)

## 1. 사전 요구 사항 (Prerequisites)

- **Java JDK 21+** (Project uses Java 21 features like Virtual Threads/Preview if enabled, and specific GC options)
- **Docker** (For PostgreSQL Database)
- **Gradle** (Included via wrapper)

## 2. 로컬 실행 (Local Development)

### 2.1 데이터베이스 실행
로컬 테스트를 위해 PostgreSQL 컨테이너를 실행해야 합니다. `PersistenceWorker`가 시작 시 필요한 테이블(`currencies`, `orders` 등)을 자동으로 생성합니다.
```bash
docker run --name exchange-db -e POSTGRES_PASSWORD=pass -p 5432:5432 -d postgres:15
```

### 2.2 빌드 (Build)
```bash
./gradlew shadowJar
```

### 2.3 전체 시스템 실행 (Run All)
스크립트 하나로 Gateway, OME, ME, Worker를 모두 실행합니다.
```bash
./run-local.sh
```
*   로그 파일: `me.log`, `ome.log`, `worker.log`, `gateway.log`

## 3. 테스트 (Testing)

### 3.1 기본 거래 테스트 (Basic Trading)
입금 -> 매수/매도 주문 -> 체결 확인.
```bash
./test-scenario.sh
```

### 3.2 오더북 및 리스크 관리 테스트 (OrderBook & Risk)
잔고 부족 시 주문 차단 확인 및 호가창 조회.
```bash
./test-orderbook.sh
```

### 3.3 주문 취소 테스트 (Cancel)
주문 생성 -> 취소 -> 오더북 제거 및 환불(Refund) 확인.
```bash
./test-cancel.sh
```

## 4. 운영 환경 배포 고려사항 (Production on EKS)

### 4.1 Aeron 설정
- **Multicast:** EKS(AWS VPC CNI) 환경에서 Aeron UDP Multicast를 사용하려면 `Transit Gateway Multicast` 설정이 필요할 수 있습니다. 초기 단계에서는 `Aeron UDP Unicast` (1:1) 또는 `Aeron MDC (Multi-Destination Cast)`를 사용하는 것이 설정상 용이합니다.
- **Shared Memory:** 단일 파드 내에서 여러 컨테이너(Sidecar 패턴)로 실행할 경우 `/dev/shm` 볼륨 공유가 필수입니다.

### 4.2 인프라 스펙
- **CPU Pinning:** 매칭 엔진(ME) 스레드는 `isolcpus` 설정된 노드에서 실행하거나, K8s `CPU Manager Policy: static`을 사용하여 전용 코어를 할당받아야 Context Switching 오버헤드를 최소화할 수 있습니다.
- **StatefulSet:** 각 엔진(Shard)은 고유한 상태를 가지므로 Deployment가 아닌 StatefulSet으로 배포해야 하며, PVC를 통해 Journal(로그)을 영구 보존해야 합니다.

### 4.3 데이터베이스
- **AWS Aurora PostgreSQL:** 고가용성 및 성능을 위해 Aurora 사용 권장.
- **Connection Pool:** HikariCP 설정을 운영 부하에 맞게 튜닝해야 합니다.
