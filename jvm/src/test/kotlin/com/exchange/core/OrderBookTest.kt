package com.exchange.core

import com.exchange.model.Order
import com.exchange.sbe.Side
import com.exchange.sbe.OrderType
import com.exchange.sbe.TimeInForce
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach

class OrderBookTest {

    private lateinit var orderBook: OrderBook

    @BeforeEach
    fun setUp() {
        orderBook = OrderBook(1)
    }

    private fun createOrder(id: Long, userId: Long, price: Long, qty: Long, side: Side): Order {
        val order = Order()
        order.set(id, userId, price, qty, side, OrderType.Limit)
        return order
    }

    @Test
    fun `should add buy order to book`() {
        val order = createOrder(1, 101, 50000, 100, Side.Buy)
        orderBook.processOrder(order) { _, _, _, _, _, _ -> }
        
        assertEquals(50000L, orderBook.getBestBid())
        assertEquals(0L, orderBook.getBestAsk())
    }

    @Test
    fun `should add sell order to book`() {
        val order = createOrder(1, 101, 50000, 100, Side.Sell)
        orderBook.processOrder(order) { _, _, _, _, _, _ -> }
        
        assertEquals(0L, orderBook.getBestBid())
        assertEquals(50000L, orderBook.getBestAsk())
    }

    @Test
    fun `should match buy and sell orders`() {
        // 1. Place Sell Order: 100 @ 50000
        val sell = createOrder(1, 101, 50000, 100, Side.Sell)
        orderBook.processOrder(sell) { _, _, _, _, _, _ -> }
        
        assertEquals(50000L, orderBook.getBestAsk())

        // 2. Place Buy Order: 50 @ 50000
        val buy = createOrder(2, 102, 50000, 50, Side.Buy)
        orderBook.processOrder(buy) { _, _, _, _, _, _ -> }

        // Expected: Partial match. Sell order remaining 50. Buy order filled.
        // Book should still have the sell order.
        assertEquals(50000L, orderBook.getBestAsk())
        
        // 3. Place another Buy Order: 50 @ 50000
        val buy2 = createOrder(3, 103, 50000, 50, Side.Buy)
        orderBook.processOrder(buy2) { _, _, _, _, _, _ -> }
        
        // Expected: Full match. Book empty.
        assertEquals(0L, orderBook.getBestAsk())
    }
    
    @Test
    fun `should match crossing orders with price improvement`() {
        // Sell at 50000
        val sell = createOrder(1, 101, 50000, 100, Side.Sell)
        orderBook.processOrder(sell) { _, _, _, _, _, _ -> }
        
        // Buy at 51000 (Cross) -> Should match at 50000 (Maker Price)
        val buy = createOrder(2, 102, 51000, 100, Side.Buy)
        orderBook.processOrder(buy) { _, _, _, _, _, _ -> }
        
        // Book should be empty
        assertEquals(0L, orderBook.getBestAsk())
        assertEquals(0L, orderBook.getBestBid())
    }

    @Test
    fun `FOK order should fill entirely if liquidity is sufficient`() {
        // 1. Maker Sell Orders: Total 100 @ 50000
        orderBook.processOrder(createOrder(1, 101, 50000, 60, Side.Sell)) { _, _, _, _, _, _ -> }
        orderBook.processOrder(createOrder(2, 102, 50000, 40, Side.Sell)) { _, _, _, _, _, _ -> }

        // 2. FOK Buy Order: 100 @ 50000
        val fokBuy = Order().apply { set(3, 103, 50000, 100, Side.Buy, OrderType.Limit, 0, TimeInForce.FOK) }
        var matchCount = 0
        orderBook.processOrder(fokBuy) { _, _, _, _, _, qty ->
            matchCount++
        }

        // Expected: Fully matched (2 makers)
        assertEquals(2, matchCount)
        assertEquals(0L, fokBuy.qty)
        assertEquals(0L, orderBook.getBestAsk())
    }

    @Test
    fun `FOK order should be killed if liquidity is insufficient`() {
        // 1. Maker Sell Order: 90 @ 50000
        orderBook.processOrder(createOrder(1, 101, 50000, 90, Side.Sell)) { _, _, _, _, _, _ -> }

        // 2. FOK Buy Order: 100 @ 50000 (Needs 100, but only 90 available)
        val fokBuy = Order().apply { set(2, 102, 50000, 100, Side.Buy, OrderType.Limit, 0, TimeInForce.FOK) }
        var matchedQty = 0L
        orderBook.processOrder(fokBuy) { _, _, _, _, _, qty ->
            matchedQty += qty
        }

        // Expected: Killed (No matches, maker order remains)
        assertEquals(0L, matchedQty)
        assertEquals(50000L, orderBook.getBestAsk()) // Maker still there
        assertEquals(0L, orderBook.getBestBid())   // Taker not added to book
    }
}
