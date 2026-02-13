# 배포 및 실행 가이드 (Deployment Guide)

## 1. 사전 요구 사항 (Prerequisites)

- **Java JDK 21+** (Amazon Corretto 권장)
- **Rust 1.75+** (Cargo 포함)
- **Docker** (PostgreSQL 실행용)
- **Python 3.x** (시뮬레이터 실행용)

## 2. 데이터베이스 설정
PostgreSQL 컨테이너를 실행합니다.
```bash
docker run --name exchange-db -e POSTGRES_PASSWORD=pass -p 5432:5432 -d postgres:15
```

## 3. 언어별 실행 방법

### 3.1 JVM (Kotlin) 버전
```bash
# 빌드 (Shadow JAR 생성)
./gradlew :jvm:shadowJar

# 실행 (8080 포트)
./run-local.sh
```
* 로그: `me.log`, `ome.log`, `worker.log`, `gateway.log`

### 3.2 Rust 버전
```bash
# 빌드
cd rust && cargo build --release && cd ..

# 실행 (8080 포트)
./run-local-rust.sh
```
* 로그: `me_rust.log`, `ome_rust.log`, `worker_rust.log`, `gateway_rust.log`

## 4. 검증 및 테스트

### 4.1 통합 정합성 테스트
서버 구동 후 모든 테이블의 데이터 정합성을 자동으로 검증합니다.
```bash
./scripts/verify-integrity.sh
```

### 4.2 대규모 시장 시뮬레이션
실제 유저들이 거래하는 것과 유사한 부하를 생성합니다 (약 2,500건의 주문/체결).
```bash
python3 scripts/simulate-market.py
```

## 5. 운영 환경 고려사항
- **Aeron IPC**: 운영 환경에서는 전용 미디어 드라이버를 실행하고 메모리 맵 파일 경로(`/dev/shm`)를 공유해야 합니다.
- **CPU Pinning**: 고성능 보장을 위해 매칭 엔진 스레드를 특정 코어에 격리하는 설정이 필요합니다.
- **Scale Factor**: 상장된 코인마다 다른 Scale 값을 `currencies` 테이블에서 관리하며, 모든 엔진은 이를 동적으로 참조하도록 구성해야 합니다.
