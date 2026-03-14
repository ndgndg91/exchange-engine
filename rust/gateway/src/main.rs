use axum::{
    extract::Query,
    response::IntoResponse,
    routing::{get, post},
    Json, Router,
};
use common::{Order, OrderType, Side, SnapshotData, TimeInForce, ipc::{EngineResponse, OmeCommand}};
use serde::{Deserialize, Serialize};
use std::io::Write;
use std::net::{SocketAddr, TcpStream};
use std::sync::atomic::{AtomicU64, Ordering};
use std::env;
use std::sync::{Arc, Mutex};
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::time::Duration;

static SEQ_ID: AtomicU64 = AtomicU64::new(1);

#[derive(Deserialize)]
struct OrderRequest {
    user_id: u64,
    symbol_id: i32,
    price: i64,
    qty: i64,
    side: u8,
    #[serde(default)]
    order_type: Option<u8>, // 1=Limit, 2=Market, 3=StopLimit, 4=StopMarket
    #[serde(default)]
    trigger_price: Option<i64>,
    #[serde(default)]
    tif: Option<u8>, // 0=GTC, 1=IOC, 2=FOK
}

#[derive(Deserialize)]
struct DepositRequest {
    user_id: u64,
    currency_id: i32,
    amount: i64,
}

#[derive(Deserialize)]
struct CancelRequest {
    user_id: u64,
    order_id: u64,
    symbol_id: i32,
}

#[derive(Deserialize)]
struct OrderBookQuery {
    #[serde(default = "default_symbol")]
    symbol_id: i32,
}

fn default_symbol() -> i32 {
    1
}

#[derive(Serialize)]
struct OrderBookResponse {
    symbol_id: i32,
    bids: Vec<[i64; 2]>,
    asks: Vec<[i64; 2]>,
}

/// Shared state for cached orderbook snapshot
struct AppState {
    snapshot: Mutex<SnapshotData>,
}

#[tokio::main]
async fn main() {
    eprintln!("Starting Rust Gateway (Axum)...");

    let state = Arc::new(AppState {
        snapshot: Mutex::new(SnapshotData {
            bids: vec![],
            asks: vec![],
        }),
    });

    // Start background task to receive snapshots from ME
    let snapshot_state = Arc::clone(&state);
    tokio::spawn(async move {
        snapshot_receiver(snapshot_state).await;
    });

    let app = Router::new()
        .route("/order", post(handle_order))
        .route("/deposit", post(handle_deposit))
        .route("/withdraw", post(handle_withdraw))
        .route("/cancel", post(handle_cancel))
        .route("/orderbook", get(handle_orderbook))
        .with_state(state);

    let addr = SocketAddr::from(([0, 0, 0, 0], 8080));
    eprintln!("Listening on {}", addr);

    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}

/// Background task: connect to ME snapshot port and update AppState
async fn snapshot_receiver(state: Arc<AppState>) {
    let snapshot_addr = env::var("ME_SNAPSHOT_ADDR").unwrap_or_else(|_| "127.0.0.1:5559".into());

    loop {
        match tokio::net::TcpStream::connect(&snapshot_addr).await {
            Ok(stream) => {
                eprintln!("Gateway: Connected to ME snapshot stream at {}", snapshot_addr);
                let reader = BufReader::new(stream);
                let mut lines = reader.lines();

                while let Ok(Some(line)) = lines.next_line().await {
                    if let Ok(resp) = serde_json::from_str::<EngineResponse>(&line) {
                        if let EngineResponse::OrderBookSnapshot { symbol_id: _, bids, asks } = resp {
                            let mut snap = state.snapshot.lock().unwrap();
                            snap.bids = bids;
                            snap.asks = asks;
                        }
                    }
                }

                eprintln!("Gateway: ME snapshot stream disconnected, reconnecting...");
            }
            Err(_) => {
                eprintln!("Gateway: Waiting for ME snapshot stream at {}...", snapshot_addr);
            }
        }
        tokio::time::sleep(Duration::from_secs(2)).await;
    }
}

async fn handle_order(Json(payload): Json<OrderRequest>) -> impl IntoResponse {
    let order_id = SEQ_ID.fetch_add(1, Ordering::SeqCst);
    let side = if payload.side == 1 {
        Side::Buy
    } else {
        Side::Sell
    };

    let order_type = match payload.order_type.unwrap_or(1) {
        2 => OrderType::Market,
        3 => OrderType::StopLimit,
        4 => OrderType::StopMarket,
        _ => OrderType::Limit,
    };

    let tif = match payload.tif.unwrap_or(0) {
        1 => TimeInForce::IOC,
        2 => TimeInForce::FOK,
        _ => TimeInForce::GTC,
    };

    let cmd = OmeCommand::Order(Order {
        order_id,
        user_id: payload.user_id,
        symbol_id: payload.symbol_id,
        price: payload.price,
        qty: payload.qty,
        side,
        timestamp: 0,
        order_type,
        time_in_force: tif,
        trigger_price: payload.trigger_price.unwrap_or(0),
    });

    send_to_ome(cmd);
    format!("Order Sent: {}", order_id)
}

async fn handle_deposit(Json(payload): Json<DepositRequest>) -> impl IntoResponse {
    let seq_id = SEQ_ID.fetch_add(1, Ordering::SeqCst);

    let cmd = OmeCommand::Deposit {
        user_id: payload.user_id,
        currency_id: payload.currency_id,
        amount: payload.amount,
        seq_id,
    };

    send_to_ome(cmd);
    format!("Deposit Sent: {}", seq_id)
}

async fn handle_withdraw(Json(payload): Json<DepositRequest>) -> impl IntoResponse {
    let seq_id = SEQ_ID.fetch_add(1, Ordering::SeqCst);

    let cmd = OmeCommand::Withdraw {
        user_id: payload.user_id,
        currency_id: payload.currency_id,
        amount: payload.amount,
        seq_id,
    };

    send_to_ome(cmd);
    format!("Withdraw Sent: {}", seq_id)
}

async fn handle_cancel(Json(payload): Json<CancelRequest>) -> impl IntoResponse {
    let seq_id = SEQ_ID.fetch_add(1, Ordering::SeqCst);

    let cmd = OmeCommand::Cancel {
        user_id: payload.user_id,
        order_id: payload.order_id,
        symbol_id: payload.symbol_id,
        seq_id,
    };

    send_to_ome(cmd);
    format!("Cancel Sent: {}", seq_id)
}

async fn handle_orderbook(
    axum::extract::State(state): axum::extract::State<Arc<AppState>>,
    Query(params): Query<OrderBookQuery>,
) -> impl IntoResponse {
    let snap = state.snapshot.lock().unwrap();
    let resp = OrderBookResponse {
        symbol_id: params.symbol_id,
        bids: snap.bids.iter().map(|&(p, q)| [p, q]).collect(),
        asks: snap.asks.iter().map(|&(p, q)| [p, q]).collect(),
    };
    Json(resp)
}

fn send_to_ome(cmd: OmeCommand) {
    let ome_addr = env::var("OME_ADDR").unwrap_or_else(|_| "127.0.0.1:5556".into());
    if let Ok(mut stream) = TcpStream::connect(&ome_addr) {
        let mut data = serde_json::to_vec(&cmd).unwrap();
        data.push(b'\n');
        let _ = stream.write_all(&data);
    }
}
