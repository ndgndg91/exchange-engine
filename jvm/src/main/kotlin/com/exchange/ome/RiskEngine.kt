package com.exchange.ome

import com.exchange.ome.model.Account
import com.exchange.sbe.Side
import com.exchange.sbe.OrderType
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap

class RiskEngine {
    private val accounts = LongObjectHashMap<Account>()
    // Tracking last processed seqId per user for idempotency
    private val lastProcessedSeqId = org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap()

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
     */
    fun preCheckOrder(userId: Long, symbolId: Int, side: Side, price: Long, qty: Long, seqId: Long, type: OrderType = OrderType.Limit): Boolean {
        if (lastProcessedSeqId.containsKey(userId) && lastProcessedSeqId.get(userId) >= seqId) {
            return false
        }

        val account = getAccount(userId)
        val currencyId = if (side == Side.Buy) 2 else 1

        val requiredAmount = if (side == Side.Buy) {
            (price * qty) / 100_000_000L
        } else {
            qty
        }

        val balance = account.getBalance(currencyId)

        if (balance.available >= requiredAmount) {
            balance.available -= requiredAmount
            balance.locked += requiredAmount
            lastProcessedSeqId.put(userId, seqId)
            return true
        }
        return false
    }
    /**
     * Handle Execution Report (Trade Settlement)
     */
    fun onTrade(makerUserId: Long, takerUserId: Long, side: Side, price: Long, qty: Long) {
        val cost = (price * qty) / 100_000_000L
        val baseCurrency = 1 // BTC
        val quoteCurrency = 2 // KRW

        // Taker Processing
        val taker = getAccount(takerUserId)
        if (side == Side.Buy) {
            val usdt = taker.getBalance(quoteCurrency)
            val actualDeduction = Math.min(cost, usdt.locked)
            val remainder = cost - actualDeduction
            usdt.locked -= actualDeduction
            usdt.available -= remainder
            taker.getBalance(baseCurrency).available += qty
        } else {
            val btc = taker.getBalance(baseCurrency)
            val actualDeduction = Math.min(qty, btc.locked)
            val remainder = qty - actualDeduction
            btc.locked -= actualDeduction
            btc.available -= remainder
            taker.getBalance(quoteCurrency).available += cost
        }

        // Maker Processing
        val maker = getAccount(makerUserId)
        if (side == Side.Buy) {
            val btc = maker.getBalance(baseCurrency)
            val actualDeduction = Math.min(qty, btc.locked)
            val remainder = qty - actualDeduction
            btc.locked -= actualDeduction
            btc.available -= remainder
            maker.getBalance(quoteCurrency).available += cost
        } else {
            val usdt = maker.getBalance(quoteCurrency)
            val actualDeduction = Math.min(cost, usdt.locked)
            val remainder = cost - actualDeduction
            usdt.locked -= actualDeduction
            usdt.available -= remainder
            maker.getBalance(baseCurrency).available += qty
        }
    }
    
    fun onDeposit(userId: Long, currencyId: Int, amount: Long) {
        getAccount(userId).getBalance(currencyId).available += amount
    }

    fun onWithdrawRequest(userId: Long, currencyId: Int, amount: Long, seqId: Long = 0): Boolean {
        if (seqId != 0L && lastProcessedSeqId.containsKey(userId) && lastProcessedSeqId.get(userId) >= seqId) {
            return false
        }

        val balance = getAccount(userId).getBalance(currencyId)
        if (balance.available >= amount) {
            balance.available -= amount
            balance.locked += amount
            if (seqId != 0L) lastProcessedSeqId.put(userId, seqId)
            return true
        }
        return false
    }
    
    fun onCancel(orderId: Long, side: Side, price: Long, qty: Long, userId: Long) {
        val isBuy = (side == Side.Buy)
        val currencyId = if (isBuy) 2 else 1
        val unlockAmount = if (isBuy) (price * qty) / 100_000_000L else qty
        
        val balance = getAccount(userId).getBalance(currencyId)
        val actualUnlock = Math.min(unlockAmount, balance.locked)
        
        balance.locked -= actualUnlock
        balance.available += actualUnlock
        println("Risk: Order $orderId Cancelled. Refunded $actualUnlock to User $userId")
    }
    
    fun deposit(userId: Long, currencyId: Int, amount: Long) {
        onDeposit(userId, currencyId, amount)
    }
}
