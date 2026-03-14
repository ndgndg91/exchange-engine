use sqlx::postgres::PgPoolOptions;
use tokio::net::TcpListener;
use tokio::io::{AsyncBufReadExt, BufReader};
use common::{Side, ipc::PersistMessage};
use sqlx::{Pool, Postgres, Transaction};

const BTC_SCALE: i64 = 100_000_000;

async fn handle_message(pool: &Pool<Postgres>, msg: PersistMessage) -> Result<(), sqlx::Error> {
    let mut tx: Transaction<'_, Postgres> = pool.begin().await?;

    match msg {
        PersistMessage::Deposit { user_id, currency_id, amount, seq_id } => {
            let res = sqlx::query(
                "INSERT INTO transfers (seq_id, user_id, currency_id, amount, type) \
                 VALUES ($1, $2, $3, $4, 'DEPOSIT') ON CONFLICT DO NOTHING"
            )
            .bind(seq_id as i64).bind(user_id as i64)
            .bind(currency_id).bind(amount)
            .execute(&mut *tx).await?;

            if res.rows_affected() > 0 {
                sqlx::query(
                    "INSERT INTO balances (user_id, currency_id, available, locked) \
                     VALUES ($1, $2, $3, 0) \
                     ON CONFLICT (user_id, currency_id) \
                     DO UPDATE SET available = balances.available + EXCLUDED.available"
                )
                .bind(user_id as i64).bind(currency_id)
                .bind(amount)
                .execute(&mut *tx).await?;
            }
        },

        PersistMessage::Withdraw { user_id, currency_id, amount, seq_id } => {
            let res = sqlx::query(
                "INSERT INTO transfers (seq_id, user_id, currency_id, amount, type) \
                 VALUES ($1, $2, $3, $4, 'WITHDRAW') ON CONFLICT DO NOTHING"
            )
            .bind(seq_id as i64).bind(user_id as i64)
            .bind(currency_id).bind(amount)
            .execute(&mut *tx).await?;

            if res.rows_affected() > 0 {
                // OME already moved available -> locked on Request. Here we finalize by deducting from locked.
                update_balance_atomic(&mut tx, user_id, currency_id, 0, -amount).await?;
            }
        },

        PersistMessage::NewOrder(order) => {
            let res = sqlx::query(
                "INSERT INTO orders (order_id, user_id, symbol_id, price, qty, side, status) \
                 VALUES ($1, $2, $3, $4, $5, $6, 'NEW') ON CONFLICT DO NOTHING"
            )
            .bind(order.order_id as i64).bind(order.user_id as i64)
            .bind(order.symbol_id).bind(order.price)
            .bind(order.qty).bind(order.side as i16)
            .execute(&mut *tx).await?;

            if res.rows_affected() > 0 {
                let is_buy = order.side == Side::Buy;
                let currency_id = if is_buy { 2 } else { 1 };
                let lock_amount = if is_buy {
                    (order.price * order.qty) / BTC_SCALE
                } else {
                    order.qty
                };

                sqlx::query(
                    "UPDATE balances SET available = available - $1, locked = locked + $2 \
                     WHERE user_id = $3 AND currency_id = $4"
                )
                .bind(lock_amount).bind(lock_amount)
                .bind(order.user_id as i64).bind(currency_id)
                .execute(&mut *tx).await?;
            }
        },

        PersistMessage::Trade {
            match_id, maker_order_id, taker_order_id,
            maker_user_id, taker_user_id, side, price, qty,
        } => {
            let res = sqlx::query(
                "INSERT INTO trades (match_id, maker_order_id, taker_order_id, price, qty, side) \
                 VALUES ($1, $2, $3, $4, $5, $6) ON CONFLICT DO NOTHING"
            )
            .bind(match_id as i64).bind(maker_order_id as i64)
            .bind(taker_order_id as i64).bind(price)
            .bind(qty).bind(side as i16)
            .execute(&mut *tx).await?;

            if res.rows_affected() > 0 {
                for oid in [maker_order_id, taker_order_id] {
                    sqlx::query(
                        "UPDATE orders SET \
                            qty = qty - $1, \
                            status = CASE WHEN qty - $1 <= 0 THEN 'FILLED' ELSE 'PARTIALLY_FILLED' END \
                         WHERE order_id = $2"
                    )
                    .bind(qty).bind(oid as i64)
                    .execute(&mut *tx).await?;
                }

                let cost = (price * qty) / BTC_SCALE;

                if side == Side::Buy {
                    // Taker=Buyer: -Locked KRW(2), +Avail BTC(1)
                    update_balance_atomic(&mut tx, taker_user_id, 2, 0, -cost).await?;
                    update_balance_atomic(&mut tx, taker_user_id, 1, qty, 0).await?;
                    // Maker=Seller: -Locked BTC(1), +Avail KRW(2)
                    update_balance_atomic(&mut tx, maker_user_id, 1, 0, -qty).await?;
                    update_balance_atomic(&mut tx, maker_user_id, 2, cost, 0).await?;
                } else {
                    // Taker=Seller: -Locked BTC(1), +Avail KRW(2)
                    update_balance_atomic(&mut tx, taker_user_id, 1, 0, -qty).await?;
                    update_balance_atomic(&mut tx, taker_user_id, 2, cost, 0).await?;
                    // Maker=Buyer: -Locked KRW(2), +Avail BTC(1)
                    update_balance_atomic(&mut tx, maker_user_id, 2, 0, -cost).await?;
                    update_balance_atomic(&mut tx, maker_user_id, 1, qty, 0).await?;
                }
            }
        },

        PersistMessage::CancelOrder {
            order_id, user_id, symbol_id: _, leaves_qty, side, price,
        } => {
            let res = sqlx::query(
                "UPDATE orders SET status = 'CANCELLED' WHERE order_id = $1 AND status != 'CANCELLED'"
            )
            .bind(order_id as i64)
            .execute(&mut *tx).await?;

            if res.rows_affected() > 0 {
                let is_buy = side == Side::Buy;
                let currency_id = if is_buy { 2 } else { 1 };
                let unlock_amount = if is_buy {
                    (price * leaves_qty) / BTC_SCALE
                } else {
                    leaves_qty
                };

                update_balance_atomic(&mut tx, user_id, currency_id, unlock_amount, -unlock_amount).await?;
                eprintln!("Persistence: Order #{} cancelled, unlocked {} for user {}", order_id, unlock_amount, user_id);
            }
        },
    }

    tx.commit().await?;
    Ok(())
}

async fn update_balance_atomic(
    tx: &mut Transaction<'_, Postgres>, 
    user_id: u64, 
    currency_id: i32, 
    avail_delta: i64, 
    lock_delta: i64
) -> Result<(), sqlx::Error> {
    sqlx::query(
        "INSERT INTO balances (user_id, currency_id, available, locked) \
         VALUES ($1, $2, $3::bigint + LEAST(0::bigint, $4::bigint), GREATEST(0::bigint, $4::bigint)) \
         ON CONFLICT (user_id, currency_id) \
         DO UPDATE SET \
            available = balances.available + \
                CASE \
                    WHEN $4::bigint < 0 AND $3::bigint > 0 THEN LEAST(-$4::bigint, balances.locked) \
                    WHEN $4::bigint < 0 THEN $3::bigint - GREATEST(0::bigint, -$4::bigint - balances.locked) \
                    ELSE $3::bigint \
                END, \
            locked = GREATEST(0::bigint, balances.locked + $4::bigint)"
    )
    .bind(user_id as i64).bind(currency_id)
    .bind(avail_delta).bind(lock_delta)
    .execute(&mut **tx).await?;
    Ok(())
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let listen_addr = std::env::var("PERSISTENCE_ADDR").unwrap_or_else(|_| "0.0.0.0:5557".into());
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
                    if let Err(e) = handle_message(&pool_ref, msg).await {
                        eprintln!("Error handling persistence message: {:?}", e);
                    }
                }
            }
        });
    }
}
