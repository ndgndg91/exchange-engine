#!/bin/bash

# Configuration
JAR_PATH="build/libs/exchange-engine-1.0-SNAPSHOT-all.jar"
AERON_DIR="/tmp/aeron-local-test"

# Cleanup previous run
echo "Cleaning up previous run..."
rm -rf $AERON_DIR
mkdir -p $AERON_DIR
pkill -f "com.exchange.MatchingServerKt"
pkill -f "com.exchange.OmeServerKt"
pkill -f "com.exchange.PersistenceWorkerKt"
pkill -f "com.exchange.GatewayServerKt"

# 0. Cleanup Database (Optional: remove if you want to keep data)
echo "Truncating database tables..."
docker exec exchange-db psql -U postgres -d exchange -c "TRUNCATE balances, orders, trades, transfers;"

# 1. Start Matching Engine (ME)
# ME will start the Embedded Media Driver
echo "Starting Matching Engine (Symbol 1: BTC/USDT)..."
java --enable-native-access=ALL-UNNAMED \
     --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
     --add-exports java.base/sun.nio.ch=ALL-UNNAMED \
     --add-exports jdk.unsupported/sun.misc=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
     --add-opens jdk.compiler/com.sun.tools.javac=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens java.base/java.io=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
     -Daeron.dir=$AERON_DIR \
     -cp $JAR_PATH \
     com.exchange.MatchingServerKt --embedded-driver > me.log 2>&1 &
ME_PID=$!
echo "Matching Engine PID: $ME_PID"

# Wait for ME to initialize Media Driver
sleep 3

# 2. Start OME Server (Risk Engine + Simulator)
# OME connects to the existing driver at AERON_DIR
echo "Starting OME Server (User Shard 1) in SERVER mode..."
java --enable-native-access=ALL-UNNAMED \
     --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
     --add-exports java.base/sun.nio.ch=ALL-UNNAMED \
     --add-exports jdk.unsupported/sun.misc=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
     --add-opens jdk.compiler/com.sun.tools.javac=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens java.base/java.io=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
     -Daeron.dir=$AERON_DIR \
     -cp $JAR_PATH \
     com.exchange.OmeServerKt > ome.log 2>&1 &
OME_PID=$!
echo "OME Server PID: $OME_PID"

# 3. Start Persistence Worker
# Worker connects to existing driver and saves to DB
echo "Starting Persistence Worker..."
java --enable-native-access=ALL-UNNAMED \
     --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
     --add-exports java.base/sun.nio.ch=ALL-UNNAMED \
     --add-exports jdk.unsupported/sun.misc=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens java.base/java.io=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
     -Daeron.dir=$AERON_DIR \
     -cp $JAR_PATH \
     com.exchange.PersistenceWorkerKt > worker.log 2>&1 &
WORKER_PID=$!
echo "Persistence Worker PID: $WORKER_PID"

# 4. Start Gateway Server
echo "Starting Gateway Server (Port 8082)..."
java --enable-native-access=ALL-UNNAMED \
     --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
     --add-exports java.base/sun.nio.ch=ALL-UNNAMED \
     --add-exports jdk.unsupported/sun.misc=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens java.base/java.io=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
     -Daeron.dir=$AERON_DIR \
     -cp $JAR_PATH \
     com.exchange.GatewayServerKt > gateway.log 2>&1 &
GATEWAY_PID=$!
echo "Gateway PID: $GATEWAY_PID"

echo "---------------------------------------------------"
echo "System Running. Logs are being written to me.log, ome.log, worker.log, gateway.log"
echo "Press Ctrl+C to stop everything."
echo "---------------------------------------------------"

# Tail logs
tail -f me.log ome.log worker.log gateway.log

# Trap Ctrl+C to kill background processes
trap "kill $ME_PID $OME_PID $WORKER_PID $GATEWAY_PID; exit" INT
