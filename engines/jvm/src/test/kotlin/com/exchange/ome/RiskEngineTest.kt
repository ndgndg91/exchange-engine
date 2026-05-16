package com.exchange.ome

import com.exchange.sbe.Side
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RiskEngineTest {

    private lateinit var riskEngine: RiskEngine
    private val USER_ID = 100L
    private val BTC = 1
    private val USDT = 2

    @BeforeEach
    fun setUp() {
        riskEngine = RiskEngine()
    }

    @Test
    fun `should pass pre-check if sufficient balance for Buy`() {
        // Deposit 100,000 USDT
        riskEngine.deposit(USER_ID, USDT, 100_000L)

        // Buy 1 BTC @ 50,000 USDT
        val success = riskEngine.preCheckOrder(USER_ID, 1, Side.Buy, 50_000L, 1L)
        
        assertTrue(success)
        val balance = riskEngine.getAccount(USER_ID).getBalance(USDT)
        assertEquals(50_000L, balance.available)
        assertEquals(50_000L, balance.locked)
    }

    @Test
    fun `should fail pre-check if insufficient balance for Buy`() {
        riskEngine.deposit(USER_ID, USDT, 40_000L)

        // Buy 1 BTC @ 50,000 USDT
        val success = riskEngine.preCheckOrder(USER_ID, 1, Side.Buy, 50_000L, 1L)
        
        assertFalse(success)
        val balance = riskEngine.getAccount(USER_ID).getBalance(USDT)
        assertEquals(40_000L, balance.available) // Unchanged
        assertEquals(0L, balance.locked)
    }

    @Test
    fun `should settle trade correctly`() {
        val MAKER = 200L
        val TAKER = 300L
        
        // Setup Initial Balances
        // Maker wants to Sell 1 BTC (Locked)
        riskEngine.deposit(MAKER, BTC, 1L)
        riskEngine.preCheckOrder(MAKER, 1, Side.Sell, 50_000L, 1L) 
        
        // Taker wants to Buy 1 BTC (Locked)
        riskEngine.deposit(TAKER, USDT, 50_000L)
        riskEngine.preCheckOrder(TAKER, 1, Side.Buy, 50_000L, 1L)
        
        // Execute Trade (Taker Buys)
        riskEngine.onTrade(MAKER, TAKER, Side.Buy, 50_000L, 1L)
        
        // Verify Taker (Bought BTC)
        val takerBtc = riskEngine.getAccount(TAKER).getBalance(BTC)
        val takerUsdt = riskEngine.getAccount(TAKER).getBalance(USDT)
        assertEquals(1L, takerBtc.available)
        assertEquals(0L, takerUsdt.locked)
        assertEquals(0L, takerUsdt.available)

        // Verify Maker (Sold BTC)
        val makerBtc = riskEngine.getAccount(MAKER).getBalance(BTC)
        val makerUsdt = riskEngine.getAccount(MAKER).getBalance(USDT)
        assertEquals(0L, makerBtc.locked)
        assertEquals(0L, makerBtc.available)
        assertEquals(50_000L, makerUsdt.available)
    }
}
