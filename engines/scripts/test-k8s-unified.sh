#!/bin/bash
set -e

# Generate a unique version tag for this run
V="v-$(date +%s)"
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SOURCE_DIR/.."

function wait_for_pods() {
    local apps=("$@")
    echo "Waiting for pods to be READY..."
    for app in "${apps[@]}"; do
        echo " > Waiting for $app..."
        until kubectl wait --for=condition=ready pod -l app=$app --timeout=10s 2>/dev/null; do
            echo "   (Still waiting for $app...)"
            sleep 2
        done
        # Verify the actual image tag being used
        ACTUAL_IMG=$(kubectl get pod -l app=$app -o jsonpath="{.items[0].spec.containers[0].image}")
        echo "   ... $app is READY! [Image: $ACTUAL_IMG]"
    done
}

function run_all_scenarios() {
    local stack_name=$1
    local svc_name=$2
    echo "--------------------------------------------------"
    echo "🚀 Running COMPREHENSIVE Tests for $stack_name Stack"
    echo "--------------------------------------------------"
    
    # 1. Port-forward gateway
    pkill -f "port-forward" || true
    kubectl port-forward svc/$svc_name 8080:8080 > /dev/null 2>&1 &
    PF_PID=$!
    sleep 5
    
    # 2. Run Scenario Suite
    echo ">>> [Scenario 1] Basic & Partial Fill..."
    $PROJECT_ROOT/scripts/test-scenario.sh
    echo ">>> [Scenario 2] Market Orders..."
    $PROJECT_ROOT/scripts/test-market-order.sh
    echo ">>> [Scenario 3] Stop Orders..."
    $PROJECT_ROOT/scripts/test-stop-order.sh
    echo ">>> [Scenario 4] Cancellations..."
    $PROJECT_ROOT/scripts/test-cancel.sh
    echo ">>> [Scenario 5] IOC/FOK..."
    $PROJECT_ROOT/scripts/test-ioc.sh
    $PROJECT_ROOT/scripts/test-fok.sh
    
    # 3. Final Integrity Audit
    echo -e "\n>>> FINAL DATA INTEGRITY AUDIT <<<"
    DB_POD=$(kubectl get pod -l app=exchange-db -o jsonpath="{.items[0].metadata.name}")
    export DB_CMD="kubectl exec $DB_POD -- psql -U postgres -d exchange -t -c"
    
    kubectl exec $DB_POD -- psql -U postgres -d exchange -c "
    SELECT 
        currency_id, 
        SUM(available + locked) as total_actual_balance,
        (SELECT COALESCE(SUM(amount), 0) FROM transfers t WHERE t.currency_id = b.currency_id AND t.type = 'DEPOSIT') as total_deposited,
        (SELECT COALESCE(SUM(amount), 0) FROM transfers t WHERE t.currency_id = b.currency_id AND t.type = 'WITHDRAW') as total_withdrawn,
        ROUND(SUM(available + locked) - ((SELECT COALESCE(SUM(amount), 0) FROM transfers t WHERE t.currency_id = b.currency_id AND t.type = 'DEPOSIT') - (SELECT COALESCE(SUM(amount), 0) FROM transfers t WHERE t.currency_id = b.currency_id AND t.type = 'WITHDRAW')), 8) as discrepancy
    FROM balances b
    GROUP BY currency_id;
    "
    
    NEG_COUNT=$(kubectl exec $DB_POD -- psql -U postgres -d exchange -t -c "SELECT count(*) FROM balances WHERE available < 0 OR locked < 0;")
    if [[ ${NEG_COUNT//[[:space:]]/} -eq 0 ]]; then
        echo " > PASS: No negative balances found."
    else
        echo " > FAIL: Negative balances detected! Count: $NEG_COUNT"
        kubectl exec $DB_POD -- psql -U postgres -d exchange -c "SELECT * FROM balances WHERE available < 0 OR locked < 0;"
        exit 1
    fi

    kill $PF_PID
}

echo "=================================================="
echo "ULTIMATE STACK INTEGRITY TEST (FORCED UPDATES)"
echo "Current Version: $V"
echo "=================================================="

# 1. Build and Load Images
echo "[Build] Packaging JVM & Rust with tag $V..."
cd $PROJECT_ROOT
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
./gradlew :jvm:shadowJar -x test

docker build -f deploy/docker/Dockerfile.jvm -t exchange-engine:$V . > /dev/null 2>&1
docker build -f deploy/docker/Dockerfile.rust -t exchange-engine-rust:$V . > /dev/null 2>&1

kind load docker-image exchange-engine:$V --name exchange-cluster
kind load docker-image exchange-engine-rust:$V --name exchange-cluster

# 2. JVM STACK
echo -e "\n[1/2] Deploying JVM Stack..."
# Use | as delimiter for sed to avoid issues with /
sed -i '' "s|image: exchange-engine:.*|image: exchange-engine:$V|g" deploy/k8s/jvm/apps.yaml

kubectl apply -f deploy/k8s/base/db.yaml
kubectl apply -f deploy/k8s/jvm/services.yaml
kubectl apply -f deploy/k8s/jvm/apps.yaml
# Force restart even if K8s thinks nothing changed
kubectl rollout restart deployment exchange-stack || true 
wait_for_pods exchange-db exchange-stack
echo "Waiting for DB init..."
sleep 15
run_all_scenarios "JVM" "gateway"

# 3. RUST STACK
echo -e "\n[2/2] Deploying RUST Stack..."
sed -i '' "s|image: exchange-engine-rust:.*|image: exchange-engine-rust:$V|g" deploy/k8s/rust/apps.yaml

# Clean up JVM to avoid port conflicts and clear DB
kubectl delete -f deploy/k8s/jvm/apps.yaml --ignore-not-found=true
DB_POD=$(kubectl get pod -l app=exchange-db -o jsonpath="{.items[0].metadata.name}")
kubectl exec $DB_POD -- psql -U postgres -d exchange -c "TRUNCATE balances, orders, trades, transfers;"

kubectl apply -f deploy/k8s/rust/apps.yaml
# Force restart for Rust too
kubectl rollout restart deployment rust-persistence || true
kubectl rollout restart deployment rust-matching-engine || true
kubectl rollout restart deployment rust-ome || true
kubectl rollout restart deployment rust-gateway || true

wait_for_pods exchange-db rust-persistence rust-matching-engine rust-ome rust-gateway
run_all_scenarios "RUST" "rust-gateway"

echo "=================================================="
echo "ALL FUNCTIONS VERIFIED WITH GUARANTEED FRESH PODS ($V)."
echo "=================================================="
