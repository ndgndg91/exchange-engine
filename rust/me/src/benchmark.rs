mod order_book;

use common::{Order, Side, OrderType};
use order_book::OrderBook;
use std::time::Instant;

fn main() {
    let mut order_book = OrderBook::new(1);
    let order_count = 100_000;

    println!("--- Rust OrderBook Benchmark (100k Orders) ---");

    let start_time = Instant::now();

    for i in 1..=order_count {
        let side = if i % 2 == 0 { Side::Buy } else { Side::Sell };
        let price = if side == Side::Buy { 50001 } else { 49999 };

        let order = Order {
            order_id: i as u64,
            user_id: if side == Side::Buy { 101 } else { 102 },
            symbol_id: 1,
            price,
            qty: 1,
            side,
            timestamp: 0,
            order_type: OrderType::Limit,
        };

        order_book.process_order(order);
    }

    let duration = start_time.elapsed();
    let total_time_ms = duration.as_secs_f64() * 1000.0;

    println!("Processed {} orders in {:.2}ms", order_count, total_time_ms);
    println!("Avg time per order: {}ns", duration.as_nanos() / order_count as u128);
}
