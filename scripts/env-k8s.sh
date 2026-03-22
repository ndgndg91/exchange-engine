#!/bin/bash
set -e

# Load common configurations
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SOURCE_DIR/common.sh"

COMMAND=$1

function setup_kind() {
    echo "[1/4] Creating fresh Kind cluster '$CLUSTER_NAME'..."
    kind create cluster --name "$CLUSTER_NAME" || true
}

function build_and_load_jvm() {
    echo "[2/4] Building JVM ShadowJar and Loading Image..."
    (cd "$PROJECT_ROOT" && ./gradlew :jvm:shadowJar -x test)
    docker build -f "$PROJECT_ROOT/deploy/docker/Dockerfile.jvm" -t "exchange-engine:$V" "$PROJECT_ROOT"
    kind load docker-image "exchange-engine:$V" --name "$CLUSTER_NAME"
}

function build_and_load_rust() {
    echo "[2/4] Building Rust Image and Loading..."
    docker build -f "$PROJECT_ROOT/deploy/docker/Dockerfile.rust" -t "exchange-engine-rust:$V" "$PROJECT_ROOT"
    kind load docker-image "exchange-engine-rust:$V" --name "$CLUSTER_NAME"
}

function deploy_jvm() {
    echo "[3/4] Deploying JVM Stack..."
    sed -i '' "s|image: exchange-engine:.*|image: exchange-engine:$V|g" "$PROJECT_ROOT/deploy/k8s/jvm/apps.yaml"
    kubectl apply -f "$PROJECT_ROOT/deploy/k8s/base/db.yaml"
    kubectl apply -f "$PROJECT_ROOT/deploy/k8s/jvm/services.yaml"
    kubectl apply -f "$PROJECT_ROOT/deploy/k8s/jvm/apps.yaml"
    wait_for_pods exchange-db exchange-stack
}

function deploy_rust() {
    echo "[3/4] Deploying Rust Stack..."
    sed -i '' "s|image: exchange-engine-rust:.*|image: exchange-engine-rust:$V|g" "$PROJECT_ROOT/deploy/k8s/rust/apps.yaml"
    kubectl apply -f "$PROJECT_ROOT/deploy/k8s/base/db.yaml"
    kubectl apply -f "$PROJECT_ROOT/deploy/k8s/rust/services.yaml"
    kubectl apply -f "$PROJECT_ROOT/deploy/k8s/rust/apps.yaml"
    wait_for_pods exchange-db rust-persistence rust-matching-engine rust-ome rust-gateway
}

function start_port_forward() {
    local svc_name=${1:-gateway}
    pkill_port_forward
    echo "[4/4] Starting Port-forward for $svc_name..."
    nohup kubectl port-forward "svc/$svc_name" 8080:8080 > pf.log 2>&1 &
    
    # Wait for connectivity
    echo "Waiting for connectivity on 127.0.0.1:8080..."
    local max_attempts=10
    local attempt=1
    until curl -s "$BASE_URL/orderbook?symbolId=1" > /dev/null; do
        if [ $attempt -eq $max_attempts ]; then
            echo " > Error: Gateway not reachable after $max_attempts attempts."
            tail -n 10 pf.log
            exit 1
        fi
        echo "   (Attempt $attempt/$max_attempts: Waiting for gateway...)"
        sleep 2
        ((attempt++))
    done
    echo " > Gateway is reachable!"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    case "$COMMAND" in
        up-jvm)
            setup_kind
            build_and_load_jvm
            deploy_jvm
            start_port_forward "gateway"
            echo "JVM Stack is UP!"
            ;;
        up-rust)
            setup_kind
            build_and_load_rust
            deploy_rust
            start_port_forward "rust-gateway"
            echo "Rust Stack is UP!"
            ;;
        down)
            bash "$SOURCE_DIR/teardown-k8s.sh"
            ;;
        *)
            echo "Usage: $0 {up-jvm|up-rust|down}"
            exit 1
            ;;
    esac
fi
