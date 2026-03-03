# 배포 및 실행 가이드 (Deployment Guide)

## 1. 사전 요구 사항 (Prerequisites)

- **Java JDK 21+** (Amazon Corretto 권장)
- **Rust 1.75+** (Cargo 포함)
- **Docker & Docker Compose**
- **Kubernetes (kind 또는 minikube)**
- **Python 3.x** (시뮬레이터 실행용)

## 2. 프로젝트 구조

```
deploy/
├── docker/                    # Dockerfile
│   ├── Dockerfile.jvm
│   └── Dockerfile.rust
├── local/                     # docker-compose (로컬 개발용)
│   ├── docker-compose.jvm.yml
│   └── docker-compose.rust.yml
└── k8s/                       # Kubernetes (클러스터 배포)
    ├── base/                  # 공통 (DB, init SQL)
    │   └── db.yaml
    ├── jvm/                   # JVM (Aeron UDP)
    │   ├── apps.yaml
    │   ├── services.yaml
    │   └── worker.yaml
    └── rust/                  # Rust (TCP JSON)
        └── apps.yaml
```

## 3. 데이터베이스 설정 (Local)
로컬에서 테스트할 경우 PostgreSQL 컨테이너를 실행합니다.
```bash
docker run --name exchange-db -e POSTGRES_PASSWORD=pass -p 5432:5432 -d postgres:15
```

## 4. 언어별 실행 방법 (Local)

### 4.1 JVM (Kotlin) 버전
```bash
# 빌드 (Shadow JAR 생성)
./gradlew :jvm:shadowJar

# 실행 (8080 포트)
./run-local.sh
```
* 로그: `me.log`, `ome.log`, `worker.log`, `gateway.log`

### 4.2 Rust 버전
```bash
# 빌드
cd rust && cargo build --release && cd ..

# 실행 (8080 포트)
./run-local-rust.sh
```
* 로그: `me_rust.log`, `ome_rust.log`, `worker_rust.log`, `gateway_rust.log`

### 4.3 Docker Compose (로컬 전체 시스템)
```bash
# JVM 버전
docker compose -f deploy/local/docker-compose.jvm.yml up --build

# Rust 버전
docker compose -f deploy/local/docker-compose.rust.yml up --build
```

## 5. Kubernetes (k8s) 배포 가이드

### 5.1 JVM 버전 (Aeron UDP)

```bash
# Kind 클러스터 생성
kind create cluster --name exchange-cluster

# JVM 빌드 및 Docker 이미지 생성
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home
./gradlew :jvm:shadowJar

for app in matching-engine ome gateway persistence-worker; do
    docker build -f deploy/docker/Dockerfile.jvm -t exchange-engine-$app:v-pure-final .
done

# 이미지를 Kind 클러스터에 로드
kind load docker-image exchange-engine-gateway:v-pure-final exchange-engine-ome:v-pure-final exchange-engine-matching-engine:v-pure-final exchange-engine-persistence-worker:v-pure-final --name exchange-cluster

# 매니페스트 적용
kubectl apply -f deploy/k8s/base/db.yaml
kubectl apply -f deploy/k8s/jvm/services.yaml
kubectl apply -f deploy/k8s/jvm/apps.yaml
```
> **참고:** `persistence-worker`는 Aeron IPC 성능 최적화를 위해 `ome` 파드 내의 사이드카(Sidecar)로 배포됩니다.

### 5.2 Rust 버전 (TCP JSON)

```bash
# Docker 이미지 빌드
docker build -f deploy/docker/Dockerfile.rust -t exchange-engine-rust:latest .

# Kind 클러스터에 로드
kind load docker-image exchange-engine-rust:latest --name exchange-cluster

# 매니페스트 적용
kubectl apply -f deploy/k8s/base/db.yaml
kubectl apply -f deploy/k8s/rust/apps.yaml
```
> Rust 버전은 Aeron/shared memory 없이 순수 TCP 통신만 사용하므로 배포가 더 간결합니다.

### 5.3 포트포워딩 및 테스트
```bash
# JVM
kubectl port-forward svc/gateway 8080:8080 &

# Rust
kubectl port-forward svc/rust-gateway 8080:8080 &

# 시뮬레이터 실행
python3 scripts/simulate-market.py
```

## 6. 검증 및 테스트

### 6.1 통합 정합성 테스트
서버 구동 후 모든 테이블의 데이터 정합성을 자동으로 검증합니다.
```bash
./scripts/verify-integrity.sh
```

### 6.2 대규모 시장 시뮬레이션
실제 유저들이 거래하는 것과 유사한 부하를 생성합니다 (약 2,500건의 주문/체결).
```bash
python3 scripts/simulate-market.py
```

## 7. 운영 환경 고려사항
- **Aeron IPC** (JVM): 운영 환경에서는 전용 미디어 드라이버를 실행하고 메모리 맵 파일 경로(`/dev/shm`)를 공유해야 합니다.
- **CPU Pinning**: 고성능 보장을 위해 매칭 엔진 스레드를 특정 코어에 격리하는 설정이 필요합니다.
- **Scale Factor**: 상장된 코인마다 다른 Scale 값을 `currencies` 테이블에서 관리하며, 모든 엔진은 이를 동적으로 참조하도록 구성해야 합니다.
