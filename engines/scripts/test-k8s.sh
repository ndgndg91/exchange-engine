#!/bin/bash
set -e

CLUSTER_NAME="exchange-cluster"
V="v-pure-clean"
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SOURCE_DIR/.."

echo "=================================================="
echo "UNIFIED K8S INTEGRITY TEST (ROBUST WAIT)"
echo "=================================================="

echo "[1/9] Cleanup cluster and local port-forwards..."
kind delete cluster --name $CLUSTER_NAME || true
pkill -f "port-forward" || true

echo "[2/9] Creating fresh kind cluster..."
kind create cluster --name $CLUSTER_NAME

echo "[3/9] Building JVM shadowJar..."
cd $PROJECT_ROOT
export JAVA_HOME="/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :jvm:shadowJar

echo "[4/9] Building Docker images with tag $V..."
for app in matching-engine ome gateway persistence-worker; do
    docker build -f deploy/docker/Dockerfile.jvm -t exchange-engine-$app:$V .
done

echo "[5/9] Loading images into kind cluster..."
for app in matching-engine ome gateway persistence-worker; do
    kind load docker-image exchange-engine-$app:$V --name $CLUSTER_NAME
done

echo "[6/9] Updating manifests with new tag $V..."
sed -i '' "s/image: exchange-engine-matching-engine:.*/image: exchange-engine-matching-engine:$V/g" deploy/k8s/jvm/apps.yaml
sed -i '' "s/image: exchange-engine-ome:.*/image: exchange-engine-ome:$V/g" deploy/k8s/jvm/apps.yaml
sed -i '' "s/image: exchange-engine-gateway:.*/image: exchange-engine-gateway:$V/g" deploy/k8s/jvm/apps.yaml
sed -i '' "s/image: exchange-engine-persistence-worker:.*/image: exchange-engine-persistence-worker:$V/g" deploy/k8s/jvm/apps.yaml

echo "[7/9] Deploying and waiting for Pods..."
kubectl apply -f deploy/k8s/base/db.yaml
kubectl apply -f deploy/k8s/jvm/services.yaml
kubectl apply -f deploy/k8s/jvm/apps.yaml

echo "Waiting for pods to be created and READY..."
for app in exchange-db matching-engine ome gateway; do
    echo " > Waiting for $app..."
    # Loop until the pod is found AND ready
    until kubectl wait --for=condition=ready pod -l app=$app --timeout=5s 2>/dev/null; do
        echo "   ... still waiting for $app pod to be created/ready"
        sleep 5
    done
    echo "   ... $app is READY!"
done

echo "[8/9] Running market simulation..."
kubectl port-forward svc/gateway 8080:8080 > /dev/null 2>&1 &
PF_PID=$!
sleep 10
python3 $PROJECT_ROOT/scripts/simulate-market.py
sleep 20 

echo -e "\n[9/9] >>> FINAL SUCCESS AUDIT <<<"
DB_POD=$(kubectl get pod -l app=exchange-db -o jsonpath="{.items[0].metadata.name}")
kubectl exec $DB_POD -- psql -U postgres -d exchange -c "
SELECT 
    currency_id, 
    SUM(available + locked) as total_actual_balance,
    (SELECT SUM(amount) FROM transfers t WHERE t.currency_id = b.currency_id AND t.type = 'DEPOSIT') as total_deposited,
    SUM(available + locked) - (SELECT SUM(amount) FROM transfers t WHERE t.currency_id = b.currency_id AND t.type = 'DEPOSIT') as discrepancy
FROM balances b
GROUP BY currency_id;
"

echo -e "\n>>> LOG SCAN FOR EXCEPTIONS <<<"
kubectl logs -l app=ome --tail=50 | grep -Ei "Exception|Error|fail" | grep -v "org.slf4j.impl.StaticLoggerBinder" || echo "CLEAN: No errors."

kill $PF_PID
echo "=================================================="
echo "TEST FINISHED."
echo "=================================================="
