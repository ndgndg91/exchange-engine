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

## 2.2 자산 정합성 규칙 (Asset Integrity Rules)
시스템은 어떠한 상황에서도 개별 사용자의 잔고가 음수가 되지 않도록 보장해야 한다. 또한 거래소 수익 계정을 포함한 전체 자산의 합은 일정해야 한다.

1. **전체 자산 보존 법칙**: 
   `Sum(User Deposits) == Sum(User Balances) + Exchange Revenue (User ID 0)`
2. **주문 시 (Risk Check)**: 
    * 지정가: `available -= (price * qty) / 10^8 + max_fee`, `locked += amount + max_fee`
    * 시장가 매수: `available -= (safety_price * qty) / 10^8 + max_fee`, `locked += amount + max_fee`
3. **체결 시 (Settlement)**: 
    * **Taker**: `locked`에서 체결 비용과 실제 수수료를 차감. 남은 수수료 잠금분은 `available`로 반환.
    * **Maker**: `locked`에서 체결 자산을 차감하고, `available`에 체결 수익을 더함. (수수료/리베이트 적용)
    * **Exchange (ID 0)**: 발생한 모든 수수료 수익(+) 또는 리베이트 지출(-)이 합산됨.

... (중략) ...

# 5. 수수료 및 보상 시스템 (Fee & Reward System)

## 5.1 수수료 단위 및 계산
* **BPS (Basis Points)**: 모든 수수료율은 정수형 BPS 단위를 사용한다. (10 BPS = 0.1%, 1 BPS = 0.01%)
* **정수 연산**: `Fee = (Cost * BPS) / 10,000` (소수점 버림 처리)

## 5.2 유연한 수수료 정책 (Dynamic Tiers)
사용자 등급(Tier) 및 마켓별 정책에 따라 Maker/Taker 수수료를 유동적으로 적용한다.
* **Positive BPS**: 사용자가 거래소에 지불하는 수수료.
* **Negative BPS (Maker Rebate)**: 거래소가 메이커에게 지급하는 보상. 유동성 공급을 장려하기 위해 사용됨.

## 5.3 유동성 보상 프로그램 (Liquidity Provider Rewards)
1. **즉시 리베이트 (Instant Rebate)**: 
   * 대상: 지정된 마켓 메이커(MM) 등급 사용자.
   * 로직: 체결 즉시 리베이트 금액(Negative BPS)이 사용자 잔고에 반영됨.
2. **사후 보상 프로그램 (Periodic Rewards)**:
   * 대상: 일반 유동성 공급자 및 이벤트 참여자.
   * 로직: 엔진 외부의 통계 시스템이 호가 유지 시간(Time-weighted Liquidity)을 측정하여 배치(Batch)로 보상 지급.

## 5.4 거래소 수익 계정 (Exchange Revenue Account)
* **User ID 0**: 시스템에서 발생하는 모든 수수료 수익과 리베이트 지출을 관리하는 가상 계정.
* 모든 체결 이벤트 시 발생하는 수수료 흐름은 반드시 이 계정을 거쳐야 하며, 이를 통해 실시간 손익(P&L) 파악 및 자산 감사가 가능하다.

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
