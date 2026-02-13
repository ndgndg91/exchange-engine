#!/bin/bash

# Configuration
RUST_BIN_DIR="rust/target/debug"

# Cleanup
echo "Cleaning up..."
pkill -f "target/debug/me" || true
pkill -f "target/debug/ome" || true
pkill -f "target/debug/gateway" || true
pkill -f "target/debug/persistence" || true
sleep 1

# DB Init
echo "Truncating DB..."
docker exec exchange-db psql -U postgres -d exchange -c "TRUNCATE balances, orders, trades, transfers;"

# Build
echo "Building..."
(cd rust && cargo build)

# Start Services
echo "Starting Persistence..."
$RUST_BIN_DIR/persistence > worker_rust.log 2>&1 &
sleep 3

echo "Starting ME..."
$RUST_BIN_DIR/me > me_rust.log 2>&1 &
sleep 3

echo "Starting OME..."
$RUST_BIN_DIR/ome > ome_rust.log 2>&1 &
sleep 1

echo "Starting Gateway..."
$RUST_BIN_DIR/gateway > gateway_rust.log 2>&1 &
sleep 3

echo "Rust services started."
ps aux | grep "target/debug" | grep -v grep
