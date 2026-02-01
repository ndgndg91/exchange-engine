#!/bin/bash

# Move to script directory to ensure relative paths work
cd "$(dirname "$0")"

function restart_engine() {
    echo "--------------------------------------------------"
    echo ">>> Restarting Exchange Engine (Clean State)..."
    echo "--------------------------------------------------"
    pkill -f "com.exchange"
    # Wait for kill
    sleep 2
    
    # Start form root directory
    (cd .. && ./run-local.sh > /dev/null 2>&1 &)
    
    # Wait for startup
    echo "Waiting 5s for startup..."
    sleep 5
}

# 1. Basic Scenario
restart_engine
echo ">>> [1/5] Running Basic Scenario..."
./test-scenario.sh

# 2. Market Order
restart_engine
echo ">>> [2/5] Running Market Order Test..."
./test-market-order.sh

# 3. Stop Order
restart_engine
echo ">>> [3/5] Running Stop Order Test..."
./test-stop-order.sh

# 4. Cancel Order
restart_engine
echo ">>> [4/5] Running Cancel Order Test..."
./test-cancel.sh

# 5. IOC Order
restart_engine
echo ">>> [5/5] Running IOC Order Test..."
./test-ioc.sh

echo "--------------------------------------------------"
echo ">>> ALL TESTS COMPLETED."
echo "--------------------------------------------------"