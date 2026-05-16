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

    private fun adjustBalance(userId: Long, currencyId: Int, availDelta: Long, lockDelta: Long) {
        val account = getAccount(userId)
        val balance = account.getBalance(currencyId)

        if (lockDelta < 0) {
            val toDeduct = -lockDelta
            val actualDeduction = Math.min(toDeduct, balance.locked)
            val remainder = toDeduct - actualDeduction
            
            balance.locked -= actualDeduction
            if (availDelta > 0) {
                // Profit scenario
                balance.available += availDelta
            } else {
                // Cost scenario: deduct remainder from available
                balance.available = Math.max(0, balance.available + availDelta - remainder)
            }
        } else {
            balance.available = Math.max(0, balance.available + availDelta)
            balance.locked = Math.max(0, balance.locked + lockDelta)
        }
    }

    /**
     * Pre-trade Risk Check (New Order)
     */
    fun preCheckOrder(userId: Long, symbolId: Int, side: Side, price: Long, qty: Long, seqId: Long, type: OrderType = OrderType.Limit): Boolean {
        if (lastProcessedSeqId.containsKey(userId) && lastProcessedSeqId.get(userId) >= seqId) {
            return false
        }

        val currencyId = if (side == Side.Buy) 2 else 1

        // Market Buy Orders (price=0) protection
        val effectivePrice = if (side == Side.Buy && price == 0L) {
            100_000_000L // 1.0 safety price
        } else {
            price
        }

        val requiredAmount = if (side == Side.Buy) {
            (effectivePrice * qty) / 100_000_000L
        } else {
            qty
        }

        val account = getAccount(userId)
        val balance = account.getBalance(currencyId)

        if (balance.available >= requiredAmount) {
            adjustBalance(userId, currencyId, -requiredAmount, requiredAmount)
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

        if (side == Side.Buy) { // Taker is Buying
            // Taker
            adjustBalance(takerUserId, quoteCurrency, 0, -cost)
            adjustBalance(takerUserId, baseCurrency, qty, 0)
            // Maker
            adjustBalance(makerUserId, baseCurrency, 0, -qty)
            adjustBalance(makerUserId, quoteCurrency, cost, 0)
        } else { // Taker is Selling
            // Taker
            adjustBalance(takerUserId, baseCurrency, 0, -qty)
            adjustBalance(takerUserId, quoteCurrency, cost, 0)
            // Maker
            adjustBalance(makerUserId, quoteCurrency, 0, -cost)
            adjustBalance(makerUserId, baseCurrency, qty, 0)
        }
    }
    
    fun onDeposit(userId: Long, currencyId: Int, amount: Long) {
        adjustBalance(userId, currencyId, amount, 0)
    }

    fun onWithdrawRequest(userId: Long, currencyId: Int, amount: Long, seqId: Long = 0): Boolean {
        if (seqId != 0L && lastProcessedSeqId.containsKey(userId) && lastProcessedSeqId.get(userId) >= seqId) {
            return false
        }

        val balance = getAccount(userId).getBalance(currencyId)
        if (balance.available >= amount) {
            adjustBalance(userId, currencyId, -amount, amount)
            if (seqId != 0L) lastProcessedSeqId.put(userId, seqId)
            return true
        }
        return false
    }
    
    fun onCancel(orderId: Long, side: Side, price: Long, qty: Long, userId: Long) {
        val isBuy = (side == Side.Buy)
        val currencyId = if (isBuy) 2 else 1
        val unlockAmount = if (isBuy) (price * qty) / 100_000_000L else qty
        
        adjustBalance(userId, currencyId, unlockAmount, -unlockAmount)
        println("Risk: Order $orderId Cancelled. Refunded $unlockAmount to User $userId")
    }
    
    fun deposit(userId: Long, currencyId: Int, amount: Long) {
        onDeposit(userId, currencyId, amount)
    }
}
