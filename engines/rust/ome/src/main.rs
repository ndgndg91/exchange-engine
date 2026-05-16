mod journal;
mod risk_engine;

use common::ipc::{EngineResponse, OmeCommand, PersistMessage};
use journal::EventJournal;
use risk_engine::RiskEngine;
use serde::{Deserialize, Serialize};
use std::io::{BufRead, BufReader, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::{Arc, Mutex};
use std::env;
use std::thread;
use std::time::Duration;

/// ME expects this tagged format for commands
#[derive(Serialize, Deserialize)]
#[serde(tag = "cmd")]
enum MeCommand {
    NewOrder(common::Order),
    CancelOrder {
        order_id: u64,
        user_id: u64,
        symbol_id: i32,
    },
}

fn send_json<T: Serialize>(stream: &mut TcpStream, msg: &T) {
    let mut data = serde_json::to_vec(msg).unwrap();
    data.push(b'\n');
    let _ = stream.write_all(&data);
    let _ = stream.flush();
}

fn main() {
    eprintln!("Starting Rust OME Server...");

    let me_addr = env::var("ME_ADDR").unwrap_or_else(|_| "127.0.0.1:5555".into());
    let persistence_addr = env::var("PERSISTENCE_ADDR").unwrap_or_else(|_| "127.0.0.1:5557".into());
    let ome_feedback_listen = env::var("OME_FEEDBACK_LISTEN").unwrap_or_else(|_| "0.0.0.0:5558".into());
    let ome_listen_addr = env::var("OME_LISTEN_ADDR").unwrap_or_else(|_| "0.0.0.0:5556".into());

    let journal_path = env::var("JOURNAL_PATH").unwrap_or_else(|_| "/tmp/exchange-journal".into());
    let mut journal = EventJournal::new(&journal_path);

    let risk_engine = Arc::new(Mutex::new(RiskEngine::new()));

    // 1. Connect to ME
    let mut me_stream = loop {
        if let Ok(s) = TcpStream::connect(&me_addr) {
            break s;
        }
        eprintln!("OME: Waiting for ME...");
        thread::sleep(Duration::from_secs(1));
    };

    // 2. Connect to Persistence
    let mut db_stream = loop {
        if let Ok(s) = TcpStream::connect(&persistence_addr) {
            break s;
        }
        eprintln!("OME: Waiting for Persistence...");
        thread::sleep(Duration::from_secs(1));
    };

    // 3. Start ME feedback listener (reverse TCP - ME connects to us on 5558)
    let feedback_risk = Arc::clone(&risk_engine);
    thread::spawn(move || {
        let feedback_listener =
            TcpListener::bind(&ome_feedback_listen).expect("Failed to bind OME feedback port");
        eprintln!("OME: Feedback listener on {}", ome_feedback_listen);

        for stream in feedback_listener.incoming() {
            if let Ok(s) = stream {
                eprintln!("OME: ME feedback connection established.");
                let risk = Arc::clone(&feedback_risk);
                thread::spawn(move || {
                    let reader = BufReader::new(s);
                    for line in reader.lines() {
                        if let Ok(l) = line {
                            if let Ok(resp) = serde_json::from_str::<EngineResponse>(&l) {
                                let mut re = risk.lock().unwrap();
                                match resp {
                                    EngineResponse::TradeExecuted {
                                        maker_user_id,
                                        taker_user_id,
                                        side,
                                        price,
                                        qty,
                                        ..
                                    } => {
                                        re.on_trade(
                                            maker_user_id,
                                            taker_user_id,
                                            side,
                                            price,
                                            qty,
                                        );
                                        eprintln!(
                                            "OME: Trade settled - maker={} taker={} price={} qty={}",
                                            maker_user_id, taker_user_id, price, qty
                                        );
                                    }
                                    EngineResponse::OrderCancelled {
                                        user_id,
                                        side,
                                        price,
                                        leaves_qty,
                                        order_id,
                                    } => {
                                        re.on_cancel(user_id, side, price, leaves_qty);
                                        eprintln!(
                                            "OME: Cancel settled - order={} user={} unlocked qty={}",
                                            order_id, user_id, leaves_qty
                                        );
                                    }
                                    EngineResponse::OrderBookSnapshot { .. } => {
                                        // Snapshot messages are for Gateway, ignore in OME
                                    }
                                }
                            }
                        }
                    }
                });
            }
        }
    });

    // 4. Main listener for Gateway commands
    let listener = TcpListener::bind(&ome_listen_addr).expect("Failed to bind OME port");
    eprintln!("OME Listening for requests on {}", ome_listen_addr);

    for stream in listener.incoming() {
        if let Ok(s) = stream {
            let reader = BufReader::new(s);
            for line in reader.lines() {
                if let Ok(l) = line {
                    if let Ok(cmd) = serde_json::from_str::<OmeCommand>(&l) {
                        let mut re = risk_engine.lock().unwrap();
                        match cmd {
                            OmeCommand::Order(order) => {
                                if re.pre_check_order(
                                    order.user_id,
                                    order.side,
                                    order.price,
                                    order.qty,
                                    order.order_id,
                                ) {
                                    drop(re); // Release lock before I/O

                                    // WAL: journal before forwarding to ME
                                    journal.write(&OmeCommand::Order(order.clone()));

                                    // Forward to ME with tagged command format
                                    send_json(
                                        &mut me_stream,
                                        &MeCommand::NewOrder(order.clone()),
                                    );

                                    // Forward to Persistence
                                    send_json(
                                        &mut db_stream,
                                        &PersistMessage::NewOrder(order),
                                    );
                                } else {
                                    eprintln!(
                                        "OME: Risk check failed for order #{} user={}",
                                        order.order_id, order.user_id
                                    );
                                }
                            }
                            OmeCommand::Deposit {
                                user_id,
                                currency_id,
                                amount,
                                seq_id,
                            } => {
                                // WAL: journal before processing
                                journal.write(&OmeCommand::Deposit { user_id, currency_id, amount, seq_id });

                                re.deposit(user_id, currency_id, amount);
                                drop(re);

                                send_json(
                                    &mut db_stream,
                                    &PersistMessage::Deposit {
                                        user_id,
                                        currency_id,
                                        amount,
                                        seq_id,
                                    },
                                );
                            }
                            OmeCommand::Withdraw {
                                user_id,
                                currency_id,
                                amount,
                                seq_id,
                            } => {
                                // WAL: journal before processing
                                journal.write(&OmeCommand::Withdraw { user_id, currency_id, amount, seq_id });

                                if re.withdraw(user_id, currency_id, amount, seq_id) {
                                    drop(re);

                                    send_json(
                                        &mut db_stream,
                                        &PersistMessage::Withdraw {
                                            user_id,
                                            currency_id,
                                            amount,
                                            seq_id,
                                        },
                                    );
                                    eprintln!("OME: Withdraw approved - user={} amount={} cur={}", user_id, amount, currency_id);
                                } else {
                                    eprintln!("OME: Withdraw rejected (Insuff. Funds) - user={} amount={} cur={}", user_id, amount, currency_id);
                                }
                            }
                            OmeCommand::Cancel {
                                user_id,
                                order_id,
                                symbol_id,
                                seq_id,
                            } => {
                                // WAL: journal before forwarding to ME
                                journal.write(&OmeCommand::Cancel { user_id, order_id, symbol_id, seq_id });

                                drop(re); // Balance unlock will happen via feedback

                                // Forward cancel to ME
                                send_json(
                                    &mut me_stream,
                                    &MeCommand::CancelOrder {
                                        order_id,
                                        user_id,
                                        symbol_id,
                                    },
                                );
                                eprintln!(
                                    "OME: Cancel forwarded to ME - order={} user={}",
                                    order_id, user_id
                                );
                            }
                        }
                    }
                }
            }
        }
    }
}
