#!/bin/bash

# Move to script directory to ensure relative paths work
cd "$(dirname "$0")"

function restart_engine() {
    echo "--------------------------------------------------"
    echo ">>> Restarting Exchange Engine (Force Clean State)..."
    echo "--------------------------------------------------"
    pkill -9 -f "com.exchange"
    sleep 2
    
    # DB Truncate
    docker exec exchange-db psql -U postgres -d exchange -c "TRUNCATE balances, orders, trades, transfers;" > /dev/null 2>&1
    
    # Start form root directory
    (cd .. && export JAVA_HOME="/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home" && export PATH="$JAVA_HOME/bin:$PATH" && ./run-local.sh > startup.log 2>&1 &)
    
    # Wait for startup
    echo "Waiting 15s for startup..."
    sleep 15
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
./test-fok.sh

echo "--------------------------------------------------"
echo ">>> ALL TESTS COMPLETED."
echo "--------------------------------------------------"