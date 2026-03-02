# 배포 및 실행 가이드 (Deployment Guide)

## 1. 사전 요구 사항 (Prerequisites)

- **Java JDK 21+** (Amazon Corretto 권장)
- **Rust 1.75+** (Cargo 포함)
- **Docker & Docker Compose** 
- **Kubernetes (kind 또는 minikube)**
- **Python 3.x** (시뮬레이터 실행용)

## 2. 데이터베이스 설정 (Local)
로컬에서 테스트할 경우 PostgreSQL 컨테이너를 실행합니다.
```bash
docker run --name exchange-db -e POSTGRES_PASSWORD=pass -p 5432:5432 -d postgres:15
```

## 3. 언어별 실행 방법 (Local)

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

## 4. Kubernetes (k8s) 배포 가이드
MSA 구조의 안정성을 테스트하기 위해 Kubernetes 로컬 클러스터(`kind`) 배포를 지원합니다. K8s 상에서는 Aeron UDP Multicast/Unicast를 활용하여 통신합니다.

### 4.1 클러스터 생성 및 이미지 빌드
```bash
# Kind 클러스터 생성
kind create cluster --name exchange-cluster

# JVM 빌드 및 Docker 이미지 생성
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home
./gradlew :jvm:shadowJar

docker build -t exchange-engine-gateway:v-pure-final -f Dockerfile --build-arg JAR_FILE=jvm/build/libs/jvm-1.0-SNAPSHOT-all.jar .
docker build -t exchange-engine-ome:v-pure-final -f Dockerfile --build-arg JAR_FILE=jvm/build/libs/jvm-1.0-SNAPSHOT-all.jar .
docker build -t exchange-engine-matching-engine:v-pure-final -f Dockerfile --build-arg JAR_FILE=jvm/build/libs/jvm-1.0-SNAPSHOT-all.jar .
docker build -t exchange-engine-persistence-worker:v-pure-final -f Dockerfile --build-arg JAR_FILE=jvm/build/libs/jvm-1.0-SNAPSHOT-all.jar .

# 이미지를 Kind 클러스터에 로드
kind load docker-image exchange-engine-gateway:v-pure-final exchange-engine-ome:v-pure-final exchange-engine-matching-engine:v-pure-final exchange-engine-persistence-worker:v-pure-final --name exchange-cluster
```

### 4.2 매니페스트 적용
```bash
# DB 배포 및 스키마 초기화
kubectl apply -f k8s/db.yaml

# 애플리케이션 서비스 배포
kubectl apply -f k8s/common.yaml
kubectl apply -f k8s/apps.yaml
```
> **참고:** `persistence-worker`는 Aeron IPC 성능 최적화 및 네트워크 트래픽 가로채기 이슈 해결을 위해 `ome` 파드 내의 사이드카(Sidecar)로 배포됩니다.

### 4.3 포트포워딩 및 테스트
```bash
# 로컬 포트 열기
kubectl port-forward svc/gateway 8080:8080 &

# 시뮬레이터 실행
python3 scripts/simulate-market.py
```

## 5. 검증 및 테스트

### 5.1 통합 정합성 테스트
서버 구동 후 모든 테이블의 데이터 정합성을 자동으로 검증합니다.
```bash
./scripts/verify-integrity.sh
```

### 5.2 대규모 시장 시뮬레이션
실제 유저들이 거래하는 것과 유사한 부하를 생성합니다 (약 2,500건의 주문/체결).
```bash
python3 scripts/simulate-market.py
```

## 6. 운영 환경 고려사항
- **Aeron IPC**: 운영 환경에서는 전용 미디어 드라이버를 실행하고 메모리 맵 파일 경로(`/dev/shm`)를 공유해야 합니다.
- **CPU Pinning**: 고성능 보장을 위해 매칭 엔진 스레드를 특정 코어에 격리하는 설정이 필요합니다.
- **Scale Factor**: 상장된 코인마다 다른 Scale 값을 `currencies` 테이블에서 관리하며, 모든 엔진은 이를 동적으로 참조하도록 구성해야 합니다.
