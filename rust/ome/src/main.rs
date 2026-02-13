mod risk_engine;

use common::{Order, ipc::{PersistMessage, OmeCommand}};
use risk_engine::RiskEngine;
use std::io::{Write, BufRead, BufReader};
use std::net::{TcpListener, TcpStream};
use std::time::Duration;
use std::thread;

fn main() {
    println!("Starting Rust OME Server...");

    let mut risk_engine = RiskEngine::new();

    // 1. Established Persistent Connections
    let mut me_stream = loop {
        if let Ok(s) = TcpStream::connect("127.0.0.1:5555") { break s; }
        println!("OME: Waiting for ME..."); thread::sleep(Duration::from_secs(1));
    };
    
    let mut db_stream = loop {
        if let Ok(s) = TcpStream::connect("127.0.0.1:5557") { break s; }
        println!("OME: Waiting for Persistence..."); thread::sleep(Duration::from_secs(1));
    };

    let listener = TcpListener::bind("127.0.0.1:5556").expect("Failed to bind OME port");
    println!("OME Listening for requests on 127.0.0.1:5556");

    for stream in listener.incoming() {
        if let Ok(s) = stream {
            let reader = BufReader::new(s);
            for line in reader.lines() {
                if let Ok(l) = line {
                    if let Ok(cmd) = serde_json::from_str::<OmeCommand>(&l) {
                        match cmd {
                            OmeCommand::Order(order) => {
                                if risk_engine.pre_check_order(order.user_id, order.side, order.price, order.qty) {
                                    // Forward using persistent streams
                                    let mut data = serde_json::to_vec(&order).unwrap();
                                    data.push(b'\n');
                                    let _ = me_stream.write_all(&data);
                                    let _ = me_stream.flush();

                                    let msg = PersistMessage::NewOrder(order);
                                    let mut p_data = serde_json::to_vec(&msg).unwrap();
                                    p_data.push(b'\n');
                                    let _ = db_stream.write_all(&p_data);
                                    let _ = db_stream.flush();
                                }
                            },
                            OmeCommand::Deposit { user_id, currency_id, amount, seq_id } => {
                                risk_engine.deposit(user_id, currency_id, amount);
                                let msg = PersistMessage::Deposit { user_id, currency_id, amount, seq_id };
                                let mut p_data = serde_json::to_vec(&msg).unwrap();
                                p_data.push(b'\n');
                                let _ = db_stream.write_all(&p_data);
                                let _ = db_stream.flush();
                            }
                        }
                    }
                }
            }
        }
    }
}
