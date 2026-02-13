use axum::{
    routing::{post},
    Json, Router,
    response::IntoResponse,
};
use serde::{Deserialize};
use std::net::{SocketAddr, TcpStream};
use std::io::Write;
use common::{Order, Side, OrderType, ipc::OmeCommand};
use std::sync::atomic::{AtomicU64, Ordering};

static SEQ_ID: AtomicU64 = AtomicU64::new(1);

#[derive(Deserialize)]
struct OrderRequest {
    user_id: u64,
    symbol_id: i32,
    price: i64,
    qty: i64,
    side: u8,
}

#[derive(Deserialize)]
struct DepositRequest {
    user_id: u64,
    currency_id: i32,
    amount: i64,
}

#[tokio::main]
async fn main() {
    println!("Starting Rust Gateway (Axum)...");

    let app = Router::new()
        .route("/order", post(handle_order))
        .route("/deposit", post(handle_deposit));

    let addr = SocketAddr::from(([0, 0, 0, 0], 8080));
    println!("Listening on {}", addr);
    
    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}

async fn handle_order(Json(payload): Json<OrderRequest>) -> impl IntoResponse {
    let order_id = SEQ_ID.fetch_add(1, Ordering::SeqCst);
    let side = if payload.side == 1 { Side::Buy } else { Side::Sell };
    
    let cmd = OmeCommand::Order(Order {
        order_id,
        user_id: payload.user_id,
        symbol_id: payload.symbol_id,
        price: payload.price,
        qty: payload.qty,
        side,
        timestamp: 0,
        order_type: OrderType::Limit,
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

fn send_to_ome(cmd: OmeCommand) {
    if let Ok(mut stream) = TcpStream::connect("127.0.0.1:5556") {
        let mut data = serde_json::to_vec(&cmd).unwrap();
        data.push(b'\n');
        let _ = stream.write_all(&data);
    }
}
