use serde::{Serialize, Deserialize};
use crate::{Order, Side};

#[derive(Serialize, Deserialize, Debug)]
pub enum PersistMessage {
    NewOrder(Order),
    Trade {
        match_id: u64,
        maker_order_id: u64,
        taker_order_id: u64,
        maker_user_id: u64,
        taker_user_id: u64,
        side: Side, // Taker side
        price: i64,
        qty: i64,
    },
    Deposit {
        user_id: u64,
        currency_id: i32,
        amount: i64,
        seq_id: u64,
    }
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(tag = "type")] 
pub enum OmeCommand {
    Order(Order),
    Deposit {
        user_id: u64,
        currency_id: i32,
        amount: i64,
        seq_id: u64,
    }
}
