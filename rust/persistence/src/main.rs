use sqlx::postgres::PgPoolOptions;
use tokio::net::TcpListener;
use tokio::io::{AsyncBufReadExt, BufReader};
use common::{Side, ipc::PersistMessage};

const BTC_SCALE: i64 = 100_000_000;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let listen_addr = std::env::var("PERSISTENCE_LISTEN_ADDR").unwrap_or_else(|_| "0.0.0.0:5557".into());
    let db_url = std::env::var("DATABASE_URL").unwrap_or_else(|_| "postgres://postgres:pass@localhost:5432/exchange".into());

    let listener = TcpListener::bind(&listen_addr).await?;
    eprintln!("Persistence Listening on {}", listen_addr);

    let pool = PgPoolOptions::new()
        .max_connections(10)
        .connect(&db_url)
        .await?;
    eprintln!("Connected to Database.");

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
                            let _ = sqlx::query(
                                "INSERT INTO transfers (seq_id, user_id, currency_id, amount, type) \
                                 VALUES ($1, $2, $3, $4, 'DEPOSIT')"
                            )
                            .bind(seq_id as i64).bind(user_id as i64)
                            .bind(currency_id).bind(amount)
                            .execute(&pool_ref).await;

                            let _ = sqlx::query(
                                "INSERT INTO balances (user_id, currency_id, available, locked) \
                                 VALUES ($1, $2, $3, 0) \
                                 ON CONFLICT (user_id, currency_id) \
                                 DO UPDATE SET available = balances.available + $4"
                            )
                            .bind(user_id as i64).bind(currency_id)
                            .bind(amount).bind(amount)
                            .execute(&pool_ref).await;
                        },

                        PersistMessage::NewOrder(order) => {
                            let side_val = order.side as i16;
                            let _ = sqlx::query(
                                "INSERT INTO orders (order_id, user_id, symbol_id, price, qty, side, status) \
                                 VALUES ($1, $2, $3, $4, $5, $6, 'NEW')"
                            )
                            .bind(order.order_id as i64).bind(order.user_id as i64)
                            .bind(order.symbol_id).bind(order.price)
                            .bind(order.qty).bind(side_val)
                            .execute(&pool_ref).await;

                            let is_buy = order.side == Side::Buy;
                            let currency_id = if is_buy { 2 } else { 1 };
                            let lock_amount = if is_buy {
                                (order.price * order.qty) / BTC_SCALE
                            } else {
                                order.qty
                            };

                            let _ = sqlx::query(
                                "UPDATE balances SET available = available - $1, locked = locked + $2 \
                                 WHERE user_id = $3 AND currency_id = $4"
                            )
                            .bind(lock_amount).bind(lock_amount)
                            .bind(order.user_id as i64).bind(currency_id)
                            .execute(&pool_ref).await;
                        },

                        PersistMessage::Trade {
                            match_id, maker_order_id, taker_order_id,
                            maker_user_id, taker_user_id, side, price, qty,
                        } => {
                            // Insert trade record
                            let _ = sqlx::query(
                                "INSERT INTO trades (match_id, maker_order_id, taker_order_id, price, qty, side) \
                                 VALUES ($1, $2, $3, $4, $5, $6)"
                            )
                            .bind(match_id as i64).bind(maker_order_id as i64)
                            .bind(taker_order_id as i64).bind(price)
                            .bind(qty).bind(side as i16)
                            .execute(&pool_ref).await;

                            // Update order status: PARTIALLY_FILLED or FILLED
                            // Decrease remaining qty and set status accordingly
                            let _ = sqlx::query(
                                "UPDATE orders SET \
                                    qty = qty - $1, \
                                    status = CASE WHEN qty - $1 <= 0 THEN 'FILLED' ELSE 'PARTIALLY_FILLED' END \
                                 WHERE order_id = $2"
                            )
                            .bind(qty).bind(maker_order_id as i64)
                            .execute(&pool_ref).await;

                            let _ = sqlx::query(
                                "UPDATE orders SET \
                                    qty = qty - $1, \
                                    status = CASE WHEN qty - $1 <= 0 THEN 'FILLED' ELSE 'PARTIALLY_FILLED' END \
                                 WHERE order_id = $2"
                            )
                            .bind(qty).bind(taker_order_id as i64)
                            .execute(&pool_ref).await;

                            let cost = (price * qty) / BTC_SCALE;

                            if side == Side::Buy {
                                // Taker=Buyer: -Locked KRW(2), +Avail BTC(1)
                                let _ = sqlx::query(
                                    "UPDATE balances SET locked = locked - $1 \
                                     WHERE user_id = $2 AND currency_id = 2"
                                ).bind(cost).bind(taker_user_id as i64).execute(&pool_ref).await;

                                let _ = sqlx::query(
                                    "INSERT INTO balances (user_id, currency_id, available, locked) \
                                     VALUES ($1, 1, $2, 0) \
                                     ON CONFLICT (user_id, currency_id) \
                                     DO UPDATE SET available = balances.available + $3"
                                ).bind(taker_user_id as i64).bind(qty).bind(qty).execute(&pool_ref).await;

                                // Maker=Seller: -Locked BTC(1), +Avail KRW(2)
                                let _ = sqlx::query(
                                    "UPDATE balances SET locked = locked - $1 \
                                     WHERE user_id = $2 AND currency_id = 1"
                                ).bind(qty).bind(maker_user_id as i64).execute(&pool_ref).await;

                                let _ = sqlx::query(
                                    "INSERT INTO balances (user_id, currency_id, available, locked) \
                                     VALUES ($1, 2, $2, 0) \
                                     ON CONFLICT (user_id, currency_id) \
                                     DO UPDATE SET available = balances.available + $3"
                                ).bind(maker_user_id as i64).bind(cost).bind(cost).execute(&pool_ref).await;
                            } else {
                                // Taker=Seller: -Locked BTC(1), +Avail KRW(2)
                                let _ = sqlx::query(
                                    "UPDATE balances SET locked = locked - $1 \
                                     WHERE user_id = $2 AND currency_id = 1"
                                ).bind(qty).bind(taker_user_id as i64).execute(&pool_ref).await;

                                let _ = sqlx::query(
                                    "INSERT INTO balances (user_id, currency_id, available, locked) \
                                     VALUES ($1, 2, $2, 0) \
                                     ON CONFLICT (user_id, currency_id) \
                                     DO UPDATE SET available = balances.available + $3"
                                ).bind(taker_user_id as i64).bind(cost).bind(cost).execute(&pool_ref).await;

                                // Maker=Buyer: -Locked KRW(2), +Avail BTC(1)
                                let _ = sqlx::query(
                                    "UPDATE balances SET locked = locked - $1 \
                                     WHERE user_id = $2 AND currency_id = 2"
                                ).bind(cost).bind(maker_user_id as i64).execute(&pool_ref).await;

                                let _ = sqlx::query(
                                    "INSERT INTO balances (user_id, currency_id, available, locked) \
                                     VALUES ($1, 1, $2, 0) \
                                     ON CONFLICT (user_id, currency_id) \
                                     DO UPDATE SET available = balances.available + $3"
                                ).bind(maker_user_id as i64).bind(qty).bind(qty).execute(&pool_ref).await;
                            }
                        },

                        PersistMessage::CancelOrder {
                            order_id, user_id, symbol_id: _, leaves_qty, side, price,
                        } => {
                            // Update order status to CANCELLED
                            let _ = sqlx::query(
                                "UPDATE orders SET status = 'CANCELLED' WHERE order_id = $1"
                            )
                            .bind(order_id as i64)
                            .execute(&pool_ref).await;

                            // Unlock balance: locked -> available
                            let is_buy = side == Side::Buy;
                            let currency_id = if is_buy { 2 } else { 1 };
                            let unlock_amount = if is_buy {
                                (price * leaves_qty) / BTC_SCALE
                            } else {
                                leaves_qty
                            };

                            let _ = sqlx::query(
                                "UPDATE balances SET \
                                    locked = locked - $1, \
                                    available = available + $2 \
                                 WHERE user_id = $3 AND currency_id = $4"
                            )
                            .bind(unlock_amount).bind(unlock_amount)
                            .bind(user_id as i64).bind(currency_id)
                            .execute(&pool_ref).await;

                            eprintln!(
                                "Persistence: Order #{} cancelled, unlocked {} for user {}",
                                order_id, unlock_amount, user_id
                            );
                        },
                    }
                }
            }
        });
    }
}
