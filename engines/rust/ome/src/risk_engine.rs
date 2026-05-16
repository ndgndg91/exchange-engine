use common::{Side, model::Balance};
use std::collections::HashMap;

const BTC_SCALE: i64 = 100_000_000;

pub struct RiskEngine {
    accounts: HashMap<u64, HashMap<i32, Balance>>,
    last_processed_seq_id: u64,
}

impl RiskEngine {
    pub fn new() -> Self {
        Self {
            accounts: HashMap::new(),
            last_processed_seq_id: 0,
        }
    }

    pub fn deposit(&mut self, user_id: u64, currency_id: i32, amount: i64) {
        let user_balances = self.accounts.entry(user_id).or_default();
        let balance = user_balances.entry(currency_id).or_default();
        balance.available += amount;
    }

    pub fn withdraw(&mut self, user_id: u64, currency_id: i32, amount: i64, seq_id: u64) -> bool {
        if seq_id != 0 && seq_id <= self.last_processed_seq_id {
            return false;
        }

        if let Some(user_balances) = self.accounts.get_mut(&user_id) {
            if let Some(balance) = user_balances.get_mut(&currency_id) {
                if balance.available >= amount {
                    balance.available -= amount;
                    if seq_id != 0 {
                        self.last_processed_seq_id = seq_id;
                    }
                    return true;
                }
            }
        }
        false
    }

    pub fn pre_check_order(&mut self, user_id: u64, side: Side, price: i64, qty: i64, seq_id: u64) -> bool {
        if seq_id != 0 && seq_id <= self.last_processed_seq_id {
            return false;
        }

        let currency_id = if side == Side::Buy { 2 } else { 1 };
        let required_amount = if side == Side::Buy {
            (price * qty) / BTC_SCALE
        } else {
            qty
        };

        let user_balances = self.accounts.entry(user_id).or_default();
        let balance = user_balances.entry(currency_id).or_default();
        
        if balance.available >= required_amount {
            balance.available -= required_amount;
            balance.locked += required_amount;
            if seq_id != 0 {
                self.last_processed_seq_id = seq_id;
            }
            return true;
        }
        false
    }

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
            self.adjust_balance(taker_user_id, 2, 0, -cost);
            self.adjust_balance(taker_user_id, 1, qty, 0);
            self.adjust_balance(maker_user_id, 1, 0, -qty);
            self.adjust_balance(maker_user_id, 2, cost, 0);
        } else {
            self.adjust_balance(taker_user_id, 1, 0, -qty);
            self.adjust_balance(taker_user_id, 2, cost, 0);
            self.adjust_balance(maker_user_id, 2, 0, -cost);
            self.adjust_balance(maker_user_id, 1, qty, 0);
        }
    }

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
        avail_delta: i64,
        lock_delta: i64,
    ) {
        let user_balances = self.accounts.entry(user_id).or_default();
        let balance = user_balances.entry(currency_id).or_default();
        
        if lock_delta < 0 {
            let to_deduct = -lock_delta;
            let actual_deduction = to_deduct.min(balance.locked);
            let remainder = to_deduct - actual_deduction;
            
            balance.locked -= actual_deduction;
            // If refunding (avail_delta > 0), only refund what was actually locked
            if avail_delta > 0 {
                balance.available += actual_deduction;
            } else {
                balance.available += avail_delta;
                balance.available -= remainder;
            }
        } else {
            balance.available += avail_delta;
            balance.locked += lock_delta;
        }
    }

    #[cfg(test)]
    pub fn get_balance(&self, user_id: u64, currency_id: i32) -> Option<&Balance> {
        self.accounts.get(&user_id)?.get(&currency_id)
    }
}
