use std::collections::{BTreeMap, VecDeque};
use common::{Order, Side};
use serde::{Serialize, Deserialize};

pub struct OrderBook {
    pub symbol_id: i32,
    bids: BTreeMap<i64, VecDeque<Order>>, // Price -> Orders (Sorted descending)
    asks: BTreeMap<i64, VecDeque<Order>>, // Price -> Orders (Sorted ascending)
    next_match_id: u64,
}

impl OrderBook {
    pub fn new(symbol_id: i32) -> Self {
        Self {
            symbol_id,
            bids: BTreeMap::new(),
            asks: BTreeMap::new(),
            next_match_id: 1,
        }
    }

    pub fn process_order(&mut self, mut taker: Order) -> Vec<MatchEvent> {
        let mut matches = Vec::new();

        if taker.side == Side::Buy {
            Self::match_order_internal(
                &mut taker, 
                &mut self.asks, 
                &mut self.bids, 
                &mut matches, 
                &mut self.next_match_id
            );
        } else {
            Self::match_order_internal(
                &mut taker, 
                &mut self.bids, 
                &mut self.asks, 
                &mut matches, 
                &mut self.next_match_id
            );
        }

        matches
    }

    fn match_order_internal(
        taker: &mut Order,
        opposing_book: &mut BTreeMap<i64, VecDeque<Order>>,
        my_book: &mut BTreeMap<i64, VecDeque<Order>>,
        matches: &mut Vec<MatchEvent>,
        next_match_id: &mut u64,
    ) {
        let is_buy = taker.side == Side::Buy;
        
        let matching_prices: Vec<i64> = if is_buy {
            opposing_book.keys().cloned().take_while(|&p| p <= taker.price).collect()
        } else {
            opposing_book.keys().cloned().rev().take_while(|&p| p >= taker.price).collect()
        };

        for price in matching_prices {
            if taker.qty <= 0 { break; }

            if let Some(orders_at_level) = opposing_book.get_mut(&price) {
                while let Some(mut maker) = orders_at_level.pop_front() {
                    let trade_qty = std::cmp::min(maker.qty, taker.qty);
                    
                    matches.push(MatchEvent {
                        match_id: *next_match_id,
                        maker_order_id: maker.order_id,
                        taker_order_id: taker.order_id,
                        maker_user_id: maker.user_id,
                        taker_user_id: taker.user_id,
                        price,
                        qty: trade_qty,
                    });
                    *next_match_id += 1;

                    maker.qty -= trade_qty;
                    taker.qty -= trade_qty;

                    if maker.qty > 0 {
                        orders_at_level.push_front(maker);
                        break;
                    }
                    if taker.qty <= 0 { break; }
                }
                if orders_at_level.is_empty() {
                    opposing_book.remove(&price);
                }
            }
        }

        if taker.qty > 0 {
            my_book.entry(taker.price).or_insert_with(VecDeque::new).push_back(taker.clone());
        }
    }
}

#[derive(Serialize, Deserialize, Debug)]
pub struct MatchEvent {
    pub match_id: u64,
    pub maker_order_id: u64,
    pub taker_order_id: u64,
    pub maker_user_id: u64,
    pub taker_user_id: u64,
    pub price: i64,
    pub qty: i64,
}
