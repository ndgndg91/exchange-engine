package com.exchange.ome

import com.exchange.ome.model.Account
import com.exchange.sbe.Side
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap

class RiskEngine {
    private val accounts = LongObjectHashMap<Account>()

    fun getAccount(userId: Long): Account {
        var account = accounts.get(userId)
        if (account == null) {
            account = Account(userId)
            accounts.put(userId, account)
        }
        return account
    }

    /**
     * Pre-trade Risk Check (New Order)
     * Returns true if valid and funds locked.
     * Returns false if insufficient funds.
     */
    fun preCheckOrder(userId: Long, symbolId: Int, side: Side, price: Long, qty: Long): Boolean {
        val account = getAccount(userId)
        
        // Simplified currency mapping for prototype:
        // Symbol 1 (BTC/USDT): Base=BTC(1), Quote=USDT(2)
        // Check SPEC or define convention. Assuming:
        // Side.Buy -> Need USDT (Quote Currency) -> Currency 2
        // Side.Sell -> Need BTC (Base Currency) -> Currency 1
        
        val currencyId = if (side == Side.Buy) 2 else 1
        val requiredAmount = if (side == Side.Buy) price * qty else qty
        
        val balance = account.getBalance(currencyId)
        
        if (balance.available >= requiredAmount) {
            balance.available -= requiredAmount
            balance.locked += requiredAmount
            return true
        }
        
        println("Risk Fail: User $userId Cur=$currencyId Avail=${balance.available} Req=$requiredAmount")
        
        return false
    }

    /**
     * Handle Execution Report (Trade Settlement)
     * Adjusts Locked and Available balances based on trade execution.
     */
    fun onTrade(makerUserId: Long, takerUserId: Long, side: Side, price: Long, qty: Long) {
        // NOTE: 'side' parameter is the Taker's side.
        
        val cost = price * qty
        val baseCurrency = 1 // BTC
        val quoteCurrency = 2 // USDT

        // Taker Processing
        val taker = getAccount(takerUserId)
        if (side == Side.Buy) {
            // Taker Bought BTC (Paid USDT)
            // Unlock used USDT, Deduct cost
            val usdt = taker.getBalance(quoteCurrency)
            usdt.locked -= cost // Assumes exact match for simplicity. In real life, might be less if price improved.
            
            // Add BTC
            val btc = taker.getBalance(baseCurrency)
            btc.available += qty
        } else {
            // Taker Sold BTC (Received USDT)
            // Unlock used BTC, Deduct qty
            val btc = taker.getBalance(baseCurrency)
            btc.locked -= qty
            
            // Add USDT
            val usdt = taker.getBalance(quoteCurrency)
            usdt.available += cost
        }

        // Maker Processing (Opposite side)
        val maker = getAccount(makerUserId)
        if (side == Side.Buy) {
            // Maker was Selling BTC
            val btc = maker.getBalance(baseCurrency)
            btc.locked -= qty
            
            val usdt = maker.getBalance(quoteCurrency)
            usdt.available += cost
        } else {
            // Maker was Buying BTC
            val usdt = maker.getBalance(quoteCurrency)
            usdt.locked -= cost
            
            val btc = maker.getBalance(baseCurrency)
            btc.available += qty
        }
    }
    
    fun onDeposit(userId: Long, currencyId: Int, amount: Long) {
        val account = getAccount(userId)
        val balance = account.getBalance(currencyId)
        balance.available += amount
    }

    fun onWithdrawRequest(userId: Long, currencyId: Int, amount: Long): Boolean {
        val account = getAccount(userId)
        val balance = account.getBalance(currencyId)
        
        if (balance.available >= amount) {
            balance.available -= amount
            balance.locked += amount
            return true
        }
        return false
    }
    
    // Unlock Funds (Refund)
    fun onCancel(orderId: Long, side: Side, price: Long, qty: Long, userId: Long) {
        val account = getAccount(userId)
        val isBuy = (side == Side.Buy)
        
        // Locked Asset: Buy -> Quote(2), Sell -> Base(1)
        val currencyId = if (isBuy) 2 else 1
        val unlockAmount = if (isBuy) price * qty else qty
        
        val balance = account.getBalance(currencyId)
        balance.locked -= unlockAmount
        balance.available += unlockAmount
        
        println("Risk: Order $orderId Cancelled. Refunded $unlockAmount to User $userId")
    }
    
    // Helper to deposit funds for testing
    fun deposit(userId: Long, currencyId: Int, amount: Long) {
        onDeposit(userId, currencyId, amount)
    }
}
