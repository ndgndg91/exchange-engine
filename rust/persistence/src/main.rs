use sqlx::postgres::PgPoolOptions;
use tokio::net::TcpListener;
use tokio::io::{AsyncBufReadExt, BufReader};
use common::{Side, ipc::PersistMessage};

const BTC_SCALE: i64 = 100_000_000;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let listener = TcpListener::bind("0.0.0.0:5557").await?;
    println!("Persistence Listening on 0.0.0.0:5557");

    let pool = PgPoolOptions::new()
        .max_connections(10)
        .connect("postgres://postgres:pass@localhost:5432/exchange")
        .await?;
    println!("Connected to Database.");

    loop {
        let (socket, _) = listener.accept().await?;
        let pool_ref = pool.clone();
        
        tokio::spawn(async move {
            let reader = BufReader::new(socket);
            let mut lines = reader.lines();

            while let Ok(Some(line)) = lines.next_line().await {
                if let Ok(msg) = serde_json::from_str::<PersistMessage>(&line) {
                    match msg {
                        PersistMessage::Deposit { user_id, currency_id, amount, seq_id } => {
                            let _ = sqlx::query("INSERT INTO transfers (seq_id, user_id, currency_id, amount, type) VALUES ($1, $2, $3, $4, 'DEPOSIT')").bind(seq_id as i64).bind(user_id as i64).bind(currency_id).bind(amount).execute(&pool_ref).await;
                            let _ = sqlx::query("INSERT INTO balances (user_id, currency_id, available) VALUES ($1, $2, $3) ON CONFLICT (user_id, currency_id) DO UPDATE SET available = balances.available + $4").bind(user_id as i64).bind(currency_id).bind(amount).bind(amount).execute(&pool_ref).await;
                        },
                        PersistMessage::NewOrder(order) => {
                            let side_val = order.side as i16;
                            let _ = sqlx::query("INSERT INTO orders (order_id, user_id, symbol_id, price, qty, side, status) VALUES ($1, $2, $3, $4, $5, $6, 'NEW')").bind(order.order_id as i64).bind(order.user_id as i64).bind(order.symbol_id).bind(order.price).bind(order.qty).bind(side_val).execute(&pool_ref).await;
                            let is_buy = order.side == Side::Buy;
                            let currency_id = if is_buy { 2 } else { 1 };
                            let lock_amount = if is_buy { (order.price * order.qty) / BTC_SCALE } else { order.qty };
                            let _ = sqlx::query("UPDATE balances SET available = available - $1, locked = locked + $2 WHERE user_id = $3 AND currency_id = $4").bind(lock_amount).bind(lock_amount).bind(order.user_id as i64).bind(currency_id).execute(&pool_ref).await;
                        },
                        PersistMessage::Trade { match_id, maker_order_id, taker_order_id, maker_user_id, taker_user_id, side, price, qty } => {
                            let _ = sqlx::query("INSERT INTO trades (match_id, maker_order_id, taker_order_id, price, qty, side) VALUES ($1, $2, $3, $4, $5, $6)").bind(match_id as i64).bind(maker_order_id as i64).bind(taker_order_id as i64).bind(price).bind(qty).bind(side as i16).execute(&pool_ref).await;
                            let _ = sqlx::query("UPDATE orders SET status = 'FILLED', qty = qty - $1 WHERE order_id IN ($2, $3)").bind(qty).bind(maker_order_id as i64).bind(taker_order_id as i64).execute(&pool_ref).await;

                            let cost = (price * qty) / BTC_SCALE;
                            
                            // Dynamic settlement based on side
                            if side == Side::Buy {
                                // Taker was Buyer (KRW -> BTC): -Locked KRW (2), +Avail BTC (1)
                                let _ = sqlx::query("UPDATE balances SET locked = locked - $1 WHERE user_id = $2 AND currency_id = 2").bind(cost).bind(taker_user_id as i64).execute(&pool_ref).await;
                                let _ = sqlx::query("INSERT INTO balances (user_id, currency_id, available) VALUES ($1, 1, $2) ON CONFLICT (user_id, currency_id) DO UPDATE SET available = balances.available + $3").bind(taker_user_id as i64).bind(qty).bind(qty).execute(&pool_ref).await;
                                
                                // Maker was Seller (BTC -> KRW): -Locked BTC (1), +Avail KRW (2)
                                let _ = sqlx::query("UPDATE balances SET locked = locked - $1 WHERE user_id = $2 AND currency_id = 1").bind(qty).bind(maker_user_id as i64).execute(&pool_ref).await;
                                let _ = sqlx::query("INSERT INTO balances (user_id, currency_id, available) VALUES ($1, 2, $2) ON CONFLICT (user_id, currency_id) DO UPDATE SET available = balances.available + $3").bind(maker_user_id as i64).bind(cost).bind(cost).execute(&pool_ref).await;
                            } else {
                                // Taker was Seller (BTC -> KRW): -Locked BTC (1), +Avail KRW (2)
                                let _ = sqlx::query("UPDATE balances SET locked = locked - $1 WHERE user_id = $2 AND currency_id = 1").bind(qty).bind(taker_user_id as i64).execute(&pool_ref).await;
                                let _ = sqlx::query("INSERT INTO balances (user_id, currency_id, available) VALUES ($1, 2, $2) ON CONFLICT (user_id, currency_id) DO UPDATE SET available = balances.available + $3").bind(taker_user_id as i64).bind(cost).bind(cost).execute(&pool_ref).await;

                                // Maker was Buyer (KRW -> BTC): -Locked KRW (2), +Avail BTC (1)
                                let _ = sqlx::query("UPDATE balances SET locked = locked - $1 WHERE user_id = $2 AND currency_id = 2").bind(cost).bind(maker_user_id as i64).execute(&pool_ref).await;
                                let _ = sqlx::query("INSERT INTO balances (user_id, currency_id, available) VALUES ($1, 1, $2) ON CONFLICT (user_id, currency_id) DO UPDATE SET available = balances.available + $3").bind(maker_user_id as i64).bind(qty).bind(qty).execute(&pool_ref).await;
                            }
                        }
                    }
                }
            }
        });
    }
}
