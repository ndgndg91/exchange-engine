mod order_book;

use common::{Order, Side, ipc::PersistMessage};
use order_book::{OrderBook};
use crossbeam::channel::{unbounded, Receiver, Sender};
use std::thread;
use std::io::{Write, BufRead, BufReader};
use std::net::{TcpListener, TcpStream};
use std::time::Duration;

pub enum EngineCommand {
    NewOrder(Order),
}

fn main() {
    eprintln!("Starting Rust Matching Engine...");

    let mut db_stream: TcpStream = loop {
        match TcpStream::connect("127.0.0.1:5557") {
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

    let (tx, rx): (Sender<EngineCommand>, Receiver<EngineCommand>) = unbounded();

    thread::spawn(move || {
        let mut order_book = OrderBook::new(1);
        while let Ok(cmd) = rx.recv() {
            match cmd {
                EngineCommand::NewOrder(order) => {
                    let taker_side = order.side;
                    let matches = order_book.process_order(order);
                    for m in matches {
                        eprintln!("MATCH: #{} | P: {} | Q: {} | Maker: {} | Taker: {}", 
                            m.match_id, m.price, m.qty, m.maker_order_id, m.taker_order_id);
                        
                        let msg = PersistMessage::Trade {
                            match_id: m.match_id,
                            maker_order_id: m.maker_order_id,
                            taker_order_id: m.taker_order_id,
                            maker_user_id: m.maker_user_id,
                            taker_user_id: m.taker_user_id,
                            side: taker_side,
                            price: m.price,
                            qty: m.qty,
                        };
                        
                        let mut data = serde_json::to_vec(&msg).unwrap();
                        data.push(b'\n');
                        let _ = db_stream.write_all(&data);
                        let _ = db_stream.flush();
                    }
                }
            }
        }
    });

    let listener = TcpListener::bind("127.0.0.1:5555").expect("ME failed to bind 5555");
    for stream in listener.incoming() {
        if let Ok(s) = stream {
            let reader = BufReader::new(s);
            let tx_clone = tx.clone();
            thread::spawn(move || {
                for line in reader.lines() {
                    if let Ok(l) = line {
                        if let Ok(order) = serde_json::from_str::<Order>(&l) {
                            tx_clone.send(EngineCommand::NewOrder(order)).unwrap();
                        }
                    }
                }
            });
        }
    }
}
