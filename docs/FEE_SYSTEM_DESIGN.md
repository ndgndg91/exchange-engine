# 수수료 및 보상 시스템 상세 설계 (Fee & Reward System Design)

본 문서는 거래소의 수익화와 유동성 확보를 위한 수수료 시스템 및 메이커 리베이트 프로그램의 상세 설계를 담고 있습니다.

## 1. 개요 (Overview)
- **목적**: 거래 수수료를 통한 수익 창출 및 메이커 리베이트를 통한 유동성 공급 유도.
- **핵심 단위**: **BPS (Basis Points)** 사용. (1 BPS = 0.01%, 100 BPS = 1%)
- **정수 연산 원칙**: 모든 수수료 계산은 정수형으로 수행하여 오차를 차단함. `(Cost * BPS) / 10,000`.

## 2. 비즈니스 로직 (Business Logic)

### 2.1 수수료 체계 (Maker vs Taker)
- **Taker Fee**: 오더북의 유동성을 즉시 제거하는 주문에 대해 상대적으로 높은 수수료 부과.
- **Maker Fee**: 오더북에 유동성을 공급하는 주문에 대해 낮은 수수료 부과 또는 리베이트 지급.
- **Negative BPS (Rebate)**: 메이커 수수료가 음수(-)인 경우, 거래소가 유저에게 자산을 지급함.

### 2.2 고객 등급제 (Fee Tiers)
유저의 거래량이나 등급에 따라 수수료율을 유동적으로 적용함.
- **Tier 0 (Regular)**: Maker 10 BPS / Taker 20 BPS
- **Tier 1 (VIP)**: Maker 5 BPS / Taker 10 BPS
- **Tier 2 (Market Maker)**: Maker -2 BPS (Rebate) / Taker 5 BPS

### 2.3 유동성 보상 프로그램 (LP Rewards)
- **즉시 리베이트**: 체결 즉시 OME 엔진 내부에서 잔고에 반영.
- **사후 보상 (Batch)**: 특정 기간 동안 호가 기여도(Depth/Duration)를 측정하여 배치로 지급 (엔진 외부 로직).

## 3. 기술적 구현 사양 (Technical Specifications)

### 3.1 OME (Order Management Engine)
- **보수적 잠금 (Conservative Locking)**: 주문 생성 시 테이커로 체결될 최악의 상황을 가정하여 `(주문 비용 + 예상 최대 수수료)`를 `locked` 자산으로 전환.
- **실시간 정산 (Settlement)**: 체결 이벤트(Match) 발생 시, 실제 역할(Maker/Taker)에 따른 수수료를 확정하고 남은 잠금분은 `available`로 반환.
- **계산식**: 
  - Taker: `locked`에서 `cost + taker_fee` 차감.
  - Maker: `locked`에서 `cost` 차감, `available`에 `cost - maker_fee` (음수면 리베이트) 반영.

### 3.2 거래소 수익 계정 (Exchange Account)
- **User ID 0**: 시스템 전체의 수수료 수익(+) 및 리베이트 지출(-)이 합산되는 가상 계정.
- **정합성 공식**: `전체 입금액 == 모든 유저 잔고의 합 + User 0의 잔고`

## 4. 현재 구현 상태 및 로드맵 (Current Status & Roadmap)

### 4.1 완료된 사항 (Done)
- [x] `SPECIFICATION.md`에 수수료 시스템 기본 원칙 및 정합성 공식 반영.
- [x] JVM/Rust 테스트 환경 자동화 및 음수 잔고 버그 수정 완료.

### 4.2 진행 중인 사항 (In Progress)
- [ ] `scripts/init.sql` 스키마 확장 (User 0 생성, `fee_tiers` 테이블 추가 등).

### 4.3 다음 단계 (Next Steps - **이어서 할 일**)
1. **Database**: `scripts/init.sql`을 수정하여 수수료 관련 테이블 및 초기 데이터를 삽입함.
2. **JVM Model**: `Account` 모델에 `tierId`, `makerBps`, `takerBps` 필드 추가 및 OME 기동 시 로드.
3. **JVM RiskEngine**: `preCheckOrder`에서 수수료 포함 자산 잠금 로직 구현 및 `onTrade` 정산 로직 업데이트.
4. **Integration Test**: 시뮬레이션 후 `User 0` 계정을 포함하여 정합성이 맞는지 검증 스크립트(`verify-integrity.sh`) 수정.

---
*Last Updated: 2026-03-22*
