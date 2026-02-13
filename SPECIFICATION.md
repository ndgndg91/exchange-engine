# 1. 개요 (Overview)

## 1.1 프로젝트 목표
본 프로젝트는 기존 RDB 중심 처리 한계를 극복하고 `Extreme Low Latency`를 보장하는 메모리 기반 매칭 엔진을 구축하며, JVM과 Rust 두 가지 구현체의 성능과 안정성을 실증하는 것을 목표로 한다.

## 1.2 핵심 설계 원칙
1. **Integer Math (No Floating Point):** 모든 계산은 정수형으로 수행하여 부동 소수점 오차를 원천 차단한다.
2. **Scale Factor (10^8):** BTC 수량은 8자리 소수점을 정수로 표현하며, 비용 계산 시 `(price * qty) / 10^8` 공식을 엄격히 적용한다.
3. **Asset Conservation:** 시스템 내 총 자산(BTC, KRW)은 이동(체결) 시에도 합계가 변하지 않아야 한다.

# 2. 시스템 아키텍처 (System Architecture)

## 2.1 컴포넌트 흐름
1. **Gateway**: HTTP 요청(JSON) 수신 및 프로토콜 변환.
2. **OME (Risk Engine)**: 사용자 잔고 확인 및 자산 잠금(Lock).
3. **ME (Matching Engine)**: 오더북 기반 매칭 및 체결 이벤트 생성.
4. **Persistence Worker**: 모든 상태 변화(주문, 체결, 잔고)를 DB에 비동기 저장.

## 2.2 데이터 정합성 규칙
* **주문 시**: `available -= amount`, `locked += amount`
* **체결 시**: 
    * Maker: `locked -= matched_qty`, `available += matched_cost`
    * Taker: `locked -= matched_cost`, `available += matched_qty`
* **취소 시**: `locked -= amount`, `available += amount`

# 3. 기술 상세 사양 (Technical Specifications)

## 3.1 스케일 팩터 및 단위
* **BTC (Base Currency)**: ID 1, Scale 8 (1 BTC = 100,000,000)
* **KRW (Quote Currency)**: ID 2, Scale 0 (1 KRW = 1)
* **USDT (Quote Currency)**: ID 3, Scale 2 (1 USDT = 100)

## 3.2 API 명세 (JSON)
### 주문 생성 (POST /order)
```json
{
  "user_id": 100,
  "symbol_id": 1,
  "price": 55000,
  "qty": 100000000,
  "side": 1
}
```
* `side`: 1 (Buy), 2 (Sell)

# 4. 검증 시나리오
* **시뮬레이션**: `scripts/simulate-market.py`를 통해 50명 이상의 유저가 동시 다발적으로 주문을 생성/체결하는 환경을 모의한다.
* **정합성 체크**: `scripts/verify-integrity.sh`를 통해 시뮬레이션 후 모든 테이블의 자산 합계와 상태값이 수학적으로 일치하는지 자동 검증한다.
