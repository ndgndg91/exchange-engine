package com.exchange.core

import com.exchange.model.Order
import com.exchange.sbe.Side
import com.exchange.sbe.OrderType
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
}
