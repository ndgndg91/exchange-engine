mod order_book;

use common::{Order, OrderType, Side, ipc::{EngineResponse, PersistMessage}};
use order_book::OrderBook;
use crossbeam::channel::{unbounded, Receiver, Sender};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::thread;
use std::io::{Write, BufRead, BufReader};
use std::net::{TcpListener, TcpStream};
use std::env;
use std::time::Duration;

/// Commands received from OME via TCP
#[derive(Serialize, Deserialize, Debug)]
#[serde(tag = "cmd")]
pub enum EngineCommand {
    NewOrder(Order),
    CancelOrder { order_id: u64, user_id: u64, symbol_id: i32 },
}

fn send_json<T: Serialize>(stream: &mut TcpStream, msg: &T) {
    let mut data = serde_json::to_vec(msg).unwrap();
    data.push(b'\n');
    let _ = stream.write_all(&data);
    let _ = stream.flush();
}

fn main() {
    eprintln!("Starting Rust Matching Engine...");

    let persistence_addr = env::var("PERSISTENCE_ADDR").unwrap_or_else(|_| "127.0.0.1:5557".into());
    let ome_feedback_addr = env::var("OME_FEEDBACK_ADDR").unwrap_or_else(|_| "127.0.0.1:5558".into());
    let me_listen_addr = env::var("ME_LISTEN_ADDR").unwrap_or_else(|_| "0.0.0.0:5555".into());

    // 1. Connect to Persistence Worker
    let mut db_stream: TcpStream = loop {
        match TcpStream::connect(&persistence_addr) {
            Ok(s) => {
                eprintln!("ME: Connected to Persistence Worker.");
                break s;
            }
            Err(e) => {
                eprintln!("ME: Waiting for Persistence Worker on 5557... ({})", e);
                thread::sleep(Duration::from_secs(1));
            }
        }
    };

    // 2. Connect to OME feedback port (reverse TCP)
    let mut ome_feedback_stream: Option<TcpStream> = None;
    for _ in 0..10 {
        match TcpStream::connect(&ome_feedback_addr) {
            Ok(s) => {
                eprintln!("ME: Connected to OME feedback port 5558.");
                ome_feedback_stream = Some(s);
                break;
            }
            Err(_) => {
                eprintln!("ME: OME feedback port 5558 not available, continuing without feedback.");
                thread::sleep(Duration::from_secs(1));
            }
        }
    }

    let (tx, rx): (Sender<EngineCommand>, Receiver<EngineCommand>) = unbounded();

    // 3. Engine processing thread
    thread::spawn(move || {
        let mut order_book = OrderBook::new(1);
        let mut stop_orders: HashMap<i32, Vec<Order>> = HashMap::new();
        let mut last_price: HashMap<i32, i64> = HashMap::new();
        stop_orders.insert(1, Vec::new());
        last_price.insert(1, 0);

        while let Ok(cmd) = rx.recv() {
            match cmd {
                EngineCommand::NewOrder(order) => {
                    let symbol_id = order.symbol_id;

                    // Stop orders: store and wait for trigger
                    if order.order_type == OrderType::StopLimit
                        || order.order_type == OrderType::StopMarket
                    {
                        eprintln!(
                            "ME: Stop Order #{} Trigger={}",
                            order.order_id, order.trigger_price
                        );
                        stop_orders.entry(symbol_id).or_default().push(order);
                        continue;
                    }

                    // Normal order processing
                    let taker_side = order.side;
                    let matches = order_book.process_order(order);
                    let mut had_match = false;

                    for m in &matches {
                        had_match = true;
                        *last_price.entry(symbol_id).or_insert(0) = m.price;

                        eprintln!(
                            "MATCH: #{} | P: {} | Q: {} | Maker: {} | Taker: {}",
                            m.match_id, m.price, m.qty, m.maker_order_id, m.taker_order_id
                        );

                        // Send to Persistence
                        send_json(
                            &mut db_stream,
                            &PersistMessage::Trade {
                                match_id: m.match_id,
                                maker_order_id: m.maker_order_id,
                                taker_order_id: m.taker_order_id,
                                maker_user_id: m.maker_user_id,
                                taker_user_id: m.taker_user_id,
                                side: taker_side,
                                price: m.price,
                                qty: m.qty,
                            },
                        );

                        // Send feedback to OME
                        if let Some(ref mut ome_stream) = ome_feedback_stream {
                            send_json(
                                ome_stream,
                                &EngineResponse::TradeExecuted {
                                    maker_order_id: m.maker_order_id,
                                    taker_order_id: m.taker_order_id,
                                    maker_user_id: m.maker_user_id,
                                    taker_user_id: m.taker_user_id,
                                    side: taker_side,
                                    price: m.price,
                                    qty: m.qty,
                                },
                            );
                        }
                    }

                    // Check stop order triggers after a match
                    if had_match {
                        check_triggers(
                            symbol_id,
                            &mut stop_orders,
                            &mut last_price,
                            &mut order_book,
                            &mut db_stream,
                            &mut ome_feedback_stream,
                        );
                    }

                    // Send OrderBook snapshot to Persistence (for Gateway to query)
                    let snap = order_book.get_snapshot(5);
                    eprintln!(
                        "SNAPSHOT: Bids={:?} Asks={:?}",
                        snap.bids, snap.asks
                    );
                }

                EngineCommand::CancelOrder {
                    order_id,
                    user_id: _,
                    symbol_id,
                } => {
                    eprintln!("ME: Cancel Request Order #{}", order_id);

                    // Try cancel from normal order book
                    if let Some(cancelled) = order_book.cancel_order(order_id) {
                        eprintln!(
                            "ME: Order #{} Cancelled. LeavesQty={}",
                            order_id, cancelled.leaves_qty
                        );

                        // Send to Persistence
                        send_json(
                            &mut db_stream,
                            &PersistMessage::CancelOrder {
                                order_id: cancelled.order_id,
                                user_id: cancelled.user_id,
                                symbol_id,
                                leaves_qty: cancelled.leaves_qty,
                                side: cancelled.side,
                                price: cancelled.price,
                            },
                        );

                        // Send feedback to OME for balance unlock
                        if let Some(ref mut ome_stream) = ome_feedback_stream {
                            send_json(
                                ome_stream,
                                &EngineResponse::OrderCancelled {
                                    order_id: cancelled.order_id,
                                    user_id: cancelled.user_id,
                                    side: cancelled.side,
                                    price: cancelled.price,
                                    leaves_qty: cancelled.leaves_qty,
                                },
                            );
                        }
                    } else {
                        // Try cancel from stop orders
                        let stops = stop_orders.entry(symbol_id).or_default();
                        if let Some(pos) = stops.iter().position(|s| s.order_id == order_id) {
                            let removed = stops.remove(pos);
                            eprintln!(
                                "ME: Stop Order #{} Cancelled (from StopBook)",
                                order_id
                            );

                            send_json(
                                &mut db_stream,
                                &PersistMessage::CancelOrder {
                                    order_id: removed.order_id,
                                    user_id: removed.user_id,
                                    symbol_id,
                                    leaves_qty: removed.qty,
                                    side: removed.side,
                                    price: removed.price,
                                },
                            );

                            if let Some(ref mut ome_stream) = ome_feedback_stream {
                                send_json(
                                    ome_stream,
                                    &EngineResponse::OrderCancelled {
                                        order_id: removed.order_id,
                                        user_id: removed.user_id,
                                        side: removed.side,
                                        price: removed.price,
                                        leaves_qty: removed.qty,
                                    },
                                );
                            }
                        } else {
                            eprintln!("ME: Order #{} Not Found for Cancellation", order_id);
                        }
                    }
                }
            }
        }
    });

    // 4. TCP listener for commands from OME
    let listener = TcpListener::bind(&me_listen_addr).expect("ME failed to bind");
    for stream in listener.incoming() {
        if let Ok(s) = stream {
            let reader = BufReader::new(s);
            let tx_clone = tx.clone();
            thread::spawn(move || {
                for line in reader.lines() {
                    if let Ok(l) = line {
                        if let Ok(cmd) = serde_json::from_str::<EngineCommand>(&l) {
                            let _ = tx_clone.send(cmd);
                        }
                    }
                }
            });
        }
    }
}

/// Check and trigger stop orders based on last traded price
fn check_triggers(
    symbol_id: i32,
    stop_orders: &mut HashMap<i32, Vec<Order>>,
    last_price: &mut HashMap<i32, i64>,
    order_book: &mut OrderBook,
    db_stream: &mut TcpStream,
    ome_feedback_stream: &mut Option<TcpStream>,
) {
    let current_price = *last_price.get(&symbol_id).unwrap_or(&0);
    if current_price == 0 {
        return;
    }

    let stops = match stop_orders.get_mut(&symbol_id) {
        Some(s) => s,
        None => return,
    };

    let mut i = 0;
    while i < stops.len() {
        let stop = &stops[i];
        let triggered = match stop.side {
            Side::Buy => current_price >= stop.trigger_price,
            Side::Sell => current_price <= stop.trigger_price,
            _ => false,
        };

        if triggered {
            let mut stop = stops.remove(i);
            eprintln!(
                "ME: STOP TRIGGERED! Order #{} at Price {}",
                stop.order_id, current_price
            );

            // Convert: StopMarket -> Market, StopLimit -> Limit
            stop.order_type = if stop.order_type == OrderType::StopMarket {
                OrderType::Market
            } else {
                OrderType::Limit
            };

            let taker_side = stop.side;
            let matches = order_book.process_order(stop);

            for m in &matches {
                *last_price.entry(symbol_id).or_insert(0) = m.price;

                send_json(
                    db_stream,
                    &PersistMessage::Trade {
                        match_id: m.match_id,
                        maker_order_id: m.maker_order_id,
                        taker_order_id: m.taker_order_id,
                        maker_user_id: m.maker_user_id,
                        taker_user_id: m.taker_user_id,
                        side: taker_side,
                        price: m.price,
                        qty: m.qty,
                    },
                );

                if let Some(ref mut ome_stream) = ome_feedback_stream {
                    send_json(
                        ome_stream,
                        &EngineResponse::TradeExecuted {
                            maker_order_id: m.maker_order_id,
                            taker_order_id: m.taker_order_id,
                            maker_user_id: m.maker_user_id,
                            taker_user_id: m.taker_user_id,
                            side: taker_side,
                            price: m.price,
                            qty: m.qty,
                        },
                    );
                }
            }
            // don't increment i, next element shifted into position
        } else {
            i += 1;
        }
    }
}
