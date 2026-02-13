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
        let user_balances = self.accounts.entry(user_id).or_insert_with(HashMap::new);
        let balance = user_balances.entry(currency_id).or_insert_with(Balance::default);
        balance.available += amount;
    }

    pub fn pre_check_order(&mut self, user_id: u64, side: Side, price: i64, qty: i64) -> bool {
        let currency_id = if side == Side::Buy { 2 } else { 1 };
        
        // Correct calculation with SCALE FACTOR
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
}
