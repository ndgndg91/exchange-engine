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
    },
    CancelOrder {
        order_id: u64,
        user_id: u64,
        symbol_id: i32,
        leaves_qty: i64,
        side: Side,
        price: i64,
    },
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
    },
    Cancel {
        user_id: u64,
        order_id: u64,
        symbol_id: i32,
        seq_id: u64,
    },
}

/// ME → OME feedback for balance settlement
#[derive(Serialize, Deserialize, Debug)]
pub enum EngineResponse {
    TradeExecuted {
        maker_order_id: u64,
        taker_order_id: u64,
        maker_user_id: u64,
        taker_user_id: u64,
        side: Side,
        price: i64,
        qty: i64,
    },
    OrderCancelled {
        order_id: u64,
        user_id: u64,
        side: Side,
        price: i64,
        leaves_qty: i64,
    },
    OrderBookSnapshot {
        symbol_id: i32,
        bids: Vec<(i64, i64)>,
        asks: Vec<(i64, i64)>,
    },
}
