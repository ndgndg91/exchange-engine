use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, Clone, Copy, PartialEq, Default)]
pub enum Side {
    Buy = 1,
    Sell = 2,
    #[default]
    NullVal = 255,
}

#[derive(Debug, Serialize, Deserialize, Clone, Copy, PartialEq, Default)]
pub enum OrderType {
    #[default]
    Limit = 1,
    Market = 2,
    StopLimit = 3,
    StopMarket = 4,
}

#[derive(Debug, Serialize, Deserialize, Clone, Copy, PartialEq, Default)]
pub enum TimeInForce {
    #[default]
    GTC = 0,
    IOC = 1,
    FOK = 2,
}

#[derive(Debug, Serialize, Deserialize, Clone, Default)]
#[serde(default)]
pub struct Order {
    pub order_id: u64,
    pub user_id: u64,
    pub symbol_id: i32,
    pub price: i64,
    pub qty: i64,
    pub side: Side,
    pub timestamp: i64,
    pub order_type: OrderType,
    pub time_in_force: TimeInForce,
    pub trigger_price: i64,
}

pub mod model {
    #[derive(Debug, Default)]
    pub struct Balance {
        pub available: i64,
        pub locked: i64,
    }
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SnapshotData {
    pub bids: Vec<(i64, i64)>, // (price, total_qty)
    pub asks: Vec<(i64, i64)>,
}

#[derive(Debug, Clone)]
pub struct CancelledOrder {
    pub order_id: u64,
    pub user_id: u64,
    pub price: i64,
    pub leaves_qty: i64,
    pub side: Side,
}

pub mod ipc;
