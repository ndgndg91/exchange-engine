#!/bin/bash

# ==================================================
# Shared Configurations and Utilities
# ==================================================

# 1. Project Paths & Environment
export PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export CLUSTER_NAME="exchange-cluster"
export BASE_URL="http://127.0.0.1:8080"
export JAVA_HOME="/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 2. Dynamic Tag Generation
export V="v-$(date +%s)"

# 3. Utility Functions
function wait_for_pods() {
    local apps=("$@")
    echo "Waiting for pods to be READY..."
    for app in "${apps[@]}"; do
        echo " > Waiting for $app..."
        until kubectl wait --for=condition=ready pod -l app=$app --timeout=10s 2>/dev/null; do
            echo "   (Still waiting for $app...)"
            sleep 2
        done
        ACTUAL_IMG=$(kubectl get pod -l app=$app -o jsonpath="{.items[0].spec.containers[0].image}")
        echo "   ... $app is READY! [Image: $ACTUAL_IMG]"
    done
}

function pkill_port_forward() {
    echo "Stopping existing port-forward processes..."
    pkill -f "kubectl port-forward" || true
    sleep 2
}

function get_db_pod() {
    kubectl get pod -l app=exchange-db -o jsonpath="{.items[0].metadata.name}"
}

function run_sql() {
    local query=$1
    local db_pod=$(get_db_pod)
    kubectl exec "$db_pod" -- psql -U postgres -d exchange -c "$query"
}

function check_db_integrity() {
    local db_pod=$(get_db_pod)
    echo -e "\n>>> DATA INTEGRITY AUDIT <<<"
    kubectl exec "$db_pod" -- psql -U postgres -d exchange -c "
    SELECT 
        currency_id, 
        SUM(available + locked) as total_actual_balance,
        (SELECT COALESCE(SUM(amount), 0) FROM transfers t WHERE t.currency_id = b.currency_id AND t.type = 'DEPOSIT') as total_deposited,
        (SELECT COALESCE(SUM(amount), 0) FROM transfers t WHERE t.currency_id = b.currency_id AND t.type = 'WITHDRAW') as total_withdrawn,
        ROUND(SUM(available + locked) - ((SELECT COALESCE(SUM(amount), 0) FROM transfers t WHERE t.currency_id = b.currency_id AND t.type = 'DEPOSIT') - (SELECT COALESCE(SUM(amount), 0) FROM transfers t WHERE t.currency_id = b.currency_id AND t.type = 'WITHDRAW')), 8) as discrepancy
    FROM balances b
    GROUP BY currency_id;
    "
    
    local neg_count=$(kubectl exec "$db_pod" -- psql -U postgres -d exchange -t -c "SELECT count(*) FROM balances WHERE available < 0 OR locked < 0;")
    if [[ ${neg_count//[[:space:]]/} -eq 0 ]]; then
        echo " > PASS: No negative balances found."
    else
        echo " > FAIL: Negative balances detected! Count: $neg_count"
        run_sql "SELECT * FROM balances WHERE available < 0 OR locked < 0;"
        exit 1
    fi
}
