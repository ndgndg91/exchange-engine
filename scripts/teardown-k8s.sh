#!/bin/bash

echo "=================================================="
echo "Tearing down Local Kubernetes Environment (Kind)"
echo "=================================================="

CLUSTER_NAME="exchange-cluster"

# 1. Stop any background port-forwarding processes
echo "[Step 1] Stopping kubectl port-forward processes..."
pkill -f "kubectl port-forward svc/gateway"
if [ $? -eq 0 ]; then
    echo " > Port-forwarding stopped."
else
    echo " > No port-forwarding processes found."
fi

# 2. Delete the kind cluster
echo -e "
[Step 2] Deleting Kind cluster '$CLUSTER_NAME'..."
kind delete cluster --name $CLUSTER_NAME

# 3. Clean up any leftover local log files or temporary aeron files (optional, but good for a full reset)
echo -e "
[Step 3] Cleaning up local temporary files..."
rm -rf /dev/shm/aeron*
rm -f me.log ome.log worker.log gateway.log

echo "=================================================="
echo "Teardown Complete! Environment is clean."
echo "=================================================="
