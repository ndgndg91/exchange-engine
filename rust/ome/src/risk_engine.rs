use common::{Side, model::Balance};
use std::collections::HashMap;

const BTC_SCALE: i64 = 100_000_000;

pub struct RiskEngine {
    accounts: HashMap<u64, HashMap<i32, Balance>>,
}

impl RiskEngine {
    pub fn new() -> Self {
        Self { accounts: HashMap::new() }
    }

    pub fn deposit(&mut self, user_id: u64, currency_id: i32, amount: i64) {
        let user_balances = self.accounts.entry(user_id).or_default();
        let balance = user_balances.entry(currency_id).or_default();
        balance.available += amount;
    }

    pub fn pre_check_order(&mut self, user_id: u64, side: Side, price: i64, qty: i64) -> bool {
        let currency_id = if side == Side::Buy { 2 } else { 1 };
        let required_amount = if side == Side::Buy {
            (price * qty) / BTC_SCALE
        } else {
            qty
        };

        if let Some(user_balances) = self.accounts.get_mut(&user_id) {
            if let Some(balance) = user_balances.get_mut(&currency_id) {
                if balance.available >= required_amount {
                    balance.available -= required_amount;
                    balance.locked += required_amount;
                    return true;
                }
            }
        }
        false
    }

    /// Settle a trade: unlock locked amounts and credit counter-party
    /// taker_side = the side of the taker (Buy or Sell)
    pub fn on_trade(
        &mut self,
        maker_user_id: u64,
        taker_user_id: u64,
        taker_side: Side,
        price: i64,
        qty: i64,
    ) {
        let cost = (price * qty) / BTC_SCALE;

        if taker_side == Side::Buy {
            // Taker was Buyer: locked KRW(2) -> released, gets BTC(1)
            self.adjust_balance(taker_user_id, 2, 0, -cost);   // locked KRW -= cost
            self.adjust_balance(taker_user_id, 1, qty, 0);     // available BTC += qty

            // Maker was Seller: locked BTC(1) -> released, gets KRW(2)
            self.adjust_balance(maker_user_id, 1, 0, -qty);    // locked BTC -= qty
            self.adjust_balance(maker_user_id, 2, cost, 0);    // available KRW += cost
        } else {
            // Taker was Seller: locked BTC(1) -> released, gets KRW(2)
            self.adjust_balance(taker_user_id, 1, 0, -qty);    // locked BTC -= qty
            self.adjust_balance(taker_user_id, 2, cost, 0);    // available KRW += cost

            // Maker was Buyer: locked KRW(2) -> released, gets BTC(1)
            self.adjust_balance(maker_user_id, 2, 0, -cost);   // locked KRW -= cost
            self.adjust_balance(maker_user_id, 1, qty, 0);     // available BTC += qty
        }
    }

    /// Unlock balance on cancel: locked -> available
    pub fn on_cancel(&mut self, user_id: u64, side: Side, price: i64, leaves_qty: i64) {
        let currency_id = if side == Side::Buy { 2 } else { 1 };
        let unlock_amount = if side == Side::Buy {
            (price * leaves_qty) / BTC_SCALE
        } else {
            leaves_qty
        };

        self.adjust_balance(user_id, currency_id, unlock_amount, -unlock_amount);
    }

    fn adjust_balance(
        &mut self,
        user_id: u64,
        currency_id: i32,
        available_delta: i64,
        locked_delta: i64,
    ) {
        let user_balances = self.accounts.entry(user_id).or_default();
        let balance = user_balances.entry(currency_id).or_default();
        balance.available += available_delta;
        balance.locked += locked_delta;
    }

    #[cfg(test)]
    pub fn get_balance(&self, user_id: u64, currency_id: i32) -> Option<&Balance> {
        self.accounts.get(&user_id)?.get(&currency_id)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn should_pass_pre_check_with_sufficient_balance() {
        let mut engine = RiskEngine::new();
        engine.deposit(1, 2, 1_000_000); // 1M KRW

        let passed = engine.pre_check_order(1, Side::Buy, 50_000, 100_000_000); // Buy 1 BTC @ 50000
        assert!(passed);

        let bal = engine.get_balance(1, 2).unwrap();
        assert_eq!(bal.available, 950_000); // 1M - 50K locked
        assert_eq!(bal.locked, 50_000);
    }

    #[test]
    fn should_fail_pre_check_with_insufficient_balance() {
        let mut engine = RiskEngine::new();
        engine.deposit(1, 2, 10_000); // Only 10K KRW

        let passed = engine.pre_check_order(1, Side::Buy, 50_000, 100_000_000); // Need 50K
        assert!(!passed);

        let bal = engine.get_balance(1, 2).unwrap();
        assert_eq!(bal.available, 10_000); // Unchanged
        assert_eq!(bal.locked, 0);
    }

    #[test]
    fn should_settle_trade_correctly() {
        let mut engine = RiskEngine::new();
        // Maker (user 1): deposits 1 BTC, sells 0.5 BTC @ 50000
        engine.deposit(1, 1, 100_000_000); // 1 BTC
        engine.pre_check_order(1, Side::Sell, 50_000, 50_000_000); // Lock 0.5 BTC

        // Taker (user 2): deposits 100K KRW, buys 0.5 BTC @ 50000
        engine.deposit(2, 2, 100_000); // 100K KRW
        engine.pre_check_order(2, Side::Buy, 50_000, 50_000_000); // Lock 25K KRW

        // Trade: 0.5 BTC @ 50000 -> cost = (50000 * 50000000) / 100000000 = 25000
        engine.on_trade(1, 2, Side::Buy, 50_000, 50_000_000);

        // Taker (buyer): -25K locked KRW, +0.5 BTC available
        let taker_krw = engine.get_balance(2, 2).unwrap();
        assert_eq!(taker_krw.available, 75_000); // 100K - 25K(locked) = 75K, unchanged
        assert_eq!(taker_krw.locked, 0); // 25K - 25K = 0

        let taker_btc = engine.get_balance(2, 1).unwrap();
        assert_eq!(taker_btc.available, 50_000_000); // +0.5 BTC

        // Maker (seller): -0.5 BTC locked, +25K KRW available
        let maker_btc = engine.get_balance(1, 1).unwrap();
        assert_eq!(maker_btc.available, 50_000_000); // 1 BTC - 0.5(locked) = 0.5
        assert_eq!(maker_btc.locked, 0); // 0.5 - 0.5 = 0

        let maker_krw = engine.get_balance(1, 2).unwrap();
        assert_eq!(maker_krw.available, 25_000); // +25K KRW
    }

    #[test]
    fn should_unlock_balance_on_cancel() {
        let mut engine = RiskEngine::new();
        engine.deposit(1, 2, 100_000); // 100K KRW
        engine.pre_check_order(1, Side::Buy, 50_000, 50_000_000); // Lock 25K

        let bal = engine.get_balance(1, 2).unwrap();
        assert_eq!(bal.available, 75_000);
        assert_eq!(bal.locked, 25_000);

        // Cancel: unlock 25K
        engine.on_cancel(1, Side::Buy, 50_000, 50_000_000);

        let bal = engine.get_balance(1, 2).unwrap();
        assert_eq!(bal.available, 100_000); // Fully restored
        assert_eq!(bal.locked, 0);
    }
}
