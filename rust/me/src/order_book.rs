use std::collections::{BTreeMap, HashMap, VecDeque};
use common::{CancelledOrder, Order, OrderType, Side, SnapshotData, TimeInForce};
use serde::{Deserialize, Serialize};

pub struct OrderBook {
    pub symbol_id: i32,
    bids: BTreeMap<i64, VecDeque<Order>>,
    asks: BTreeMap<i64, VecDeque<Order>>,
    order_index: HashMap<u64, (i64, Side)>, // order_id -> (price, side)
    next_match_id: u64,
}

impl OrderBook {
    pub fn new(symbol_id: i32) -> Self {
        Self {
            symbol_id,
            bids: BTreeMap::new(),
            asks: BTreeMap::new(),
            order_index: HashMap::new(),
            next_match_id: 1,
        }
    }

    pub fn process_order(&mut self, mut taker: Order) -> Vec<MatchEvent> {
        let mut matches = Vec::new();

        if taker.side == Side::Buy {
            self.match_buy(&mut taker, &mut matches);
        } else {
            self.match_sell(&mut taker, &mut matches);
        }

        matches
    }

    fn match_buy(&mut self, taker: &mut Order, matches: &mut Vec<MatchEvent>) {
        let is_market = taker.order_type == OrderType::Market;

        // FOK Pre-check
        if taker.time_in_force == TimeInForce::FOK {
            let mut available_qty: i64 = 0;
            for (&price, orders) in self.asks.iter() {
                if !is_market && price > taker.price {
                    break; // asks are ascending, no more matching prices
                }
                for o in orders {
                    available_qty += o.qty;
                }
                if available_qty >= taker.qty {
                    break;
                }
            }
            if available_qty < taker.qty {
                // Cannot fill entirely, return without matching. Caller will detect taker.qty > 0 and cancel.
                return;
            }
        }

        // Collect matching price levels (ascending for buy taker vs asks)
        let matching_prices: Vec<i64> = if is_market {
            self.asks.keys().cloned().collect()
        } else {
            self.asks
                .keys()
                .cloned()
                .take_while(|&p| p <= taker.price)
                .collect()
        };

        for price in matching_prices {
            if taker.qty <= 0 {
                break;
            }
            self.match_at_level(taker, price, true, matches);
        }

        // Rest in book: only GTC Limit orders
        if taker.qty > 0
            && taker.order_type != OrderType::Market
            && taker.time_in_force != TimeInForce::IOC
            && taker.time_in_force != TimeInForce::FOK
        {
            self.order_index
                .insert(taker.order_id, (taker.price, taker.side));
            self.bids
                .entry(taker.price)
                .or_insert_with(VecDeque::new)
                .push_back(taker.clone());
        }
    }

    fn match_sell(&mut self, taker: &mut Order, matches: &mut Vec<MatchEvent>) {
        let is_market = taker.order_type == OrderType::Market;

        // FOK Pre-check
        if taker.time_in_force == TimeInForce::FOK {
            let mut available_qty: i64 = 0;
            for (&price, orders) in self.bids.iter().rev() {
                if !is_market && price < taker.price {
                    break; // bids descending, no more matching prices
                }
                for o in orders {
                    available_qty += o.qty;
                }
                if available_qty >= taker.qty {
                    break;
                }
            }
            if available_qty < taker.qty {
                // Cannot fill entirely, return without matching. Caller will detect taker.qty > 0 and cancel.
                return;
            }
        }

        // Collect matching price levels (descending for sell taker vs bids)
        let matching_prices: Vec<i64> = if is_market {
            self.bids.keys().cloned().rev().collect()
        } else {
            self.bids
                .keys()
                .cloned()
                .rev()
                .take_while(|&p| p >= taker.price)
                .collect()
        };

        for price in matching_prices {
            if taker.qty <= 0 {
                break;
            }
            self.match_at_level(taker, price, false, matches);
        }

        // Rest in book: only GTC Limit orders
        if taker.qty > 0
            && taker.order_type != OrderType::Market
            && taker.time_in_force != TimeInForce::IOC
            && taker.time_in_force != TimeInForce::FOK
        {
            self.order_index
                .insert(taker.order_id, (taker.price, taker.side));
            self.asks
                .entry(taker.price)
                .or_insert_with(VecDeque::new)
                .push_back(taker.clone());
        }
    }

    fn match_at_level(
        &mut self,
        taker: &mut Order,
        price: i64,
        taker_is_buy: bool,
        matches: &mut Vec<MatchEvent>,
    ) {
        let opposing_book = if taker_is_buy {
            &mut self.asks
        } else {
            &mut self.bids
        };

        let orders_at_level = match opposing_book.get_mut(&price) {
            Some(q) => q,
            None => return,
        };

        let mut i = 0;
        while i < orders_at_level.len() && taker.qty > 0 {
            let maker = &orders_at_level[i];

            // Self-Trade Protection: remove maker silently
            if maker.user_id == taker.user_id {
                let removed = orders_at_level.remove(i).unwrap();
                self.order_index.remove(&removed.order_id);
                continue; // don't increment i
            }

            let trade_qty = std::cmp::min(orders_at_level[i].qty, taker.qty);

            matches.push(MatchEvent {
                match_id: self.next_match_id,
                maker_order_id: orders_at_level[i].order_id,
                taker_order_id: taker.order_id,
                maker_user_id: orders_at_level[i].user_id,
                taker_user_id: taker.user_id,
                price,
                qty: trade_qty,
            });
            self.next_match_id += 1;

            orders_at_level[i].qty -= trade_qty;
            taker.qty -= trade_qty;

            if orders_at_level[i].qty == 0 {
                let removed = orders_at_level.remove(i).unwrap();
                self.order_index.remove(&removed.order_id);
                // don't increment i
            } else {
                i += 1;
            }
        }

        if orders_at_level.is_empty() {
            opposing_book.remove(&price);
        }
    }

    /// Cancel an order by order_id. Returns CancelledOrder with leaves_qty if found.
    pub fn cancel_order(&mut self, order_id: u64) -> Option<CancelledOrder> {
        let (price, side) = self.order_index.remove(&order_id)?;

        let book = if side == Side::Buy {
            &mut self.bids
        } else {
            &mut self.asks
        };

        let orders_at_level = book.get_mut(&price)?;

        let pos = orders_at_level
            .iter()
            .position(|o| o.order_id == order_id)?;
        let removed = orders_at_level.remove(pos).unwrap();

        if orders_at_level.is_empty() {
            book.remove(&price);
        }

        Some(CancelledOrder {
            order_id: removed.order_id,
            user_id: removed.user_id,
            price: removed.price,
            leaves_qty: removed.qty,
            side: removed.side,
        })
    }

    /// Get top-N price level snapshot for bids and asks.
    pub fn get_snapshot(&self, depth: usize) -> SnapshotData {
        let bids: Vec<(i64, i64)> = self
            .bids
            .iter()
            .rev()
            .take(depth)
            .map(|(&price, orders)| {
                let total_qty: i64 = orders.iter().map(|o| o.qty).sum();
                (price, total_qty)
            })
            .collect();

        let asks: Vec<(i64, i64)> = self
            .asks
            .iter()
            .take(depth)
            .map(|(&price, orders)| {
                let total_qty: i64 = orders.iter().map(|o| o.qty).sum();
                (price, total_qty)
            })
            .collect();

        SnapshotData { bids, asks }
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

#[cfg(test)]
mod tests {
    use super::*;

    fn make_order(
        order_id: u64,
        user_id: u64,
        price: i64,
        qty: i64,
        side: Side,
        order_type: OrderType,
        tif: TimeInForce,
    ) -> Order {
        Order {
            order_id,
            user_id,
            symbol_id: 1,
            price,
            qty,
            side,
            timestamp: 0,
            order_type,
            time_in_force: tif,
            trigger_price: 0,
        }
    }

    fn limit_order(order_id: u64, user_id: u64, price: i64, qty: i64, side: Side) -> Order {
        make_order(order_id, user_id, price, qty, side, OrderType::Limit, TimeInForce::GTC)
    }

    #[test]
    fn should_add_buy_and_sell_orders_to_book() {
        let mut book = OrderBook::new(1);

        // Add buy at 100, sell at 200 (no crossing)
        let matches = book.process_order(limit_order(1, 10, 100, 50, Side::Buy));
        assert!(matches.is_empty());

        let matches = book.process_order(limit_order(2, 20, 200, 30, Side::Sell));
        assert!(matches.is_empty());

        let snap = book.get_snapshot(5);
        assert_eq!(snap.bids, vec![(100, 50)]);
        assert_eq!(snap.asks, vec![(200, 30)]);
    }

    #[test]
    fn should_match_buy_and_sell_orders() {
        let mut book = OrderBook::new(1);

        // Sell 50 @ 100
        book.process_order(limit_order(1, 10, 100, 50, Side::Sell));

        // Buy 30 @ 100 -> match 30, sell 20 remains
        let matches = book.process_order(limit_order(2, 20, 100, 30, Side::Buy));
        assert_eq!(matches.len(), 1);
        assert_eq!(matches[0].price, 100);
        assert_eq!(matches[0].qty, 30);
        assert_eq!(matches[0].maker_order_id, 1);
        assert_eq!(matches[0].taker_order_id, 2);

        let snap = book.get_snapshot(5);
        assert_eq!(snap.asks, vec![(100, 20)]); // 50 - 30 = 20 remains
        assert!(snap.bids.is_empty());
    }

    #[test]
    fn should_match_with_price_improvement() {
        let mut book = OrderBook::new(1);

        // Sell 10 @ 95
        book.process_order(limit_order(1, 10, 95, 10, Side::Sell));

        // Buy 10 @ 100 -> match at 95 (maker price)
        let matches = book.process_order(limit_order(2, 20, 100, 10, Side::Buy));
        assert_eq!(matches.len(), 1);
        assert_eq!(matches[0].price, 95); // Price improvement: maker price
        assert_eq!(matches[0].qty, 10);
    }

    #[test]
    fn should_match_market_order_ignoring_price() {
        let mut book = OrderBook::new(1);

        // Sell 10 @ 100, Sell 10 @ 200
        book.process_order(limit_order(1, 10, 100, 10, Side::Sell));
        book.process_order(limit_order(2, 10, 200, 10, Side::Sell));

        // Market buy 15 (price=0 doesn't matter)
        let market_buy = make_order(3, 20, 0, 15, Side::Buy, OrderType::Market, TimeInForce::GTC);
        let matches = book.process_order(market_buy);

        assert_eq!(matches.len(), 2);
        assert_eq!(matches[0].price, 100);
        assert_eq!(matches[0].qty, 10);
        assert_eq!(matches[1].price, 200);
        assert_eq!(matches[1].qty, 5);

        // Market order remainder should NOT rest in book
        let snap = book.get_snapshot(5);
        assert!(snap.bids.is_empty());
        assert_eq!(snap.asks, vec![(200, 5)]); // 10 - 5 = 5 remains
    }

    #[test]
    fn should_discard_ioc_remainder() {
        let mut book = OrderBook::new(1);

        // Sell 5 @ 100
        book.process_order(limit_order(1, 10, 100, 5, Side::Sell));

        // IOC buy 10 @ 100 -> match 5, discard remaining 5
        let ioc_buy = make_order(2, 20, 100, 10, Side::Buy, OrderType::Limit, TimeInForce::IOC);
        let matches = book.process_order(ioc_buy);

        assert_eq!(matches.len(), 1);
        assert_eq!(matches[0].qty, 5);

        // Remainder should NOT be in book
        let snap = book.get_snapshot(5);
        assert!(snap.bids.is_empty());
        assert!(snap.asks.is_empty());
    }

    #[test]
    fn should_fill_fok_order_if_liquidity_sufficient() {
        let mut book = OrderBook::new(1);

        // Sell 10 @ 100
        book.process_order(limit_order(1, 10, 100, 10, Side::Sell));

        // FOK buy 10 @ 100 -> full fill
        let fok_buy = make_order(2, 20, 100, 10, Side::Buy, OrderType::Limit, TimeInForce::FOK);
        let matches = book.process_order(fok_buy);

        assert_eq!(matches.len(), 1);
        assert_eq!(matches[0].qty, 10);

        let snap = book.get_snapshot(5);
        assert!(snap.asks.is_empty());
    }

    #[test]
    fn should_kill_fok_order_if_liquidity_insufficient() {
        let mut book = OrderBook::new(1);

        // Sell 5 @ 100 (only 5 available)
        book.process_order(limit_order(1, 10, 100, 5, Side::Sell));

        // FOK buy 10 @ 100 -> cannot fill entirely, kill
        let fok_buy = make_order(2, 20, 100, 10, Side::Buy, OrderType::Limit, TimeInForce::FOK);
        let matches = book.process_order(fok_buy);

        assert!(matches.is_empty()); // No matches (killed)

        // Original sell should remain untouched
        let snap = book.get_snapshot(5);
        assert_eq!(snap.asks, vec![(100, 5)]);
        assert!(snap.bids.is_empty()); // FOK should not rest in book
    }

    #[test]
    fn should_cancel_existing_order() {
        let mut book = OrderBook::new(1);

        book.process_order(limit_order(1, 10, 100, 50, Side::Buy));

        let cancelled = book.cancel_order(1);
        assert!(cancelled.is_some());
        let c = cancelled.unwrap();
        assert_eq!(c.order_id, 1);
        assert_eq!(c.leaves_qty, 50);
        assert_eq!(c.price, 100);
        assert_eq!(c.side, Side::Buy);

        let snap = book.get_snapshot(5);
        assert!(snap.bids.is_empty());
    }

    #[test]
    fn should_return_none_for_nonexistent_cancel() {
        let mut book = OrderBook::new(1);
        assert!(book.cancel_order(999).is_none());
    }

    #[test]
    fn should_apply_self_trade_protection() {
        let mut book = OrderBook::new(1);

        // User 10: Sell 10 @ 100
        book.process_order(limit_order(1, 10, 100, 10, Side::Sell));

        // Same user 10: Buy 10 @ 100 -> STP: maker removed, no match
        let matches = book.process_order(limit_order(2, 10, 100, 10, Side::Buy));
        assert!(matches.is_empty());

        // Sell order should be removed (STP)
        let snap = book.get_snapshot(5);
        assert!(snap.asks.is_empty());
        // Buy order should rest in book (no match happened)
        assert_eq!(snap.bids, vec![(100, 10)]);
    }

    #[test]
    fn should_return_correct_snapshot() {
        let mut book = OrderBook::new(1);

        // Add multiple bids and asks
        book.process_order(limit_order(1, 10, 100, 10, Side::Buy));
        book.process_order(limit_order(2, 10, 99, 20, Side::Buy));
        book.process_order(limit_order(3, 10, 98, 30, Side::Buy));

        book.process_order(limit_order(4, 20, 101, 5, Side::Sell));
        book.process_order(limit_order(5, 20, 102, 15, Side::Sell));
        book.process_order(limit_order(6, 20, 103, 25, Side::Sell));

        let snap = book.get_snapshot(5);

        // Bids: descending by price
        assert_eq!(snap.bids, vec![(100, 10), (99, 20), (98, 30)]);
        // Asks: ascending by price
        assert_eq!(snap.asks, vec![(101, 5), (102, 15), (103, 25)]);
    }
}
