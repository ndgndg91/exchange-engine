#!/bin/bash
set -e

# Load common configurations
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SOURCE_DIR/common.sh"
source "$SOURCE_DIR/env-k8s.sh"

echo "--------------------------------------------------"
echo "🚀 Initializing Test Environment"
# 1. 포트 포워딩 재기동 및 확인 (gateway 서비스가 기본)
# 만약 Rust 스택 테스트라면 env-k8s.sh up-rust 실행 시 이미 설정되어 있겠지만,
# 여기서 한 번 더 확인하여 안전을 보장합니다.
if kubectl get svc rust-gateway > /dev/null 2>&1; then
    start_port_forward "rust-gateway"
else
    start_port_forward "gateway"
fi

echo "--------------------------------------------------"
echo "🚀 Running COMPREHENSIVE Tests"
echo "Target URL: $BASE_URL"
echo "--------------------------------------------------"

# 1. Run Scenario Suite
echo ">>> [Scenario 1] Basic & Partial Fill..."
bash "$SOURCE_DIR/test-scenario.sh"
sleep 2

echo ">>> [Scenario 2] Market Orders..."
bash "$SOURCE_DIR/test-market-order.sh"
sleep 2

echo ">>> [Scenario 3] Stop Orders..."
bash "$SOURCE_DIR/test-stop-order.sh"
sleep 2

echo ">>> [Scenario 4] Cancellations..."
bash "$SOURCE_DIR/test-cancel.sh"
sleep 2

echo ">>> [Scenario 5] IOC/FOK..."
bash "$SOURCE_DIR/test-ioc.sh"
bash "$SOURCE_DIR/test-fok.sh"
sleep 2

echo ">>> [Scenario 6] High-volume Market Simulation..."
python3 "$SOURCE_DIR/simulate-market.py"

# 2. Final Integrity Audit
check_db_integrity

echo "--------------------------------------------------"
echo "✅ ALL TESTS COMPLETED SUCCESSFULLY!"
echo "--------------------------------------------------"
