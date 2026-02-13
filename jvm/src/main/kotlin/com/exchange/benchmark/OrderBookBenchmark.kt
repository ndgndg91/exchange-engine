package com.exchange.benchmark

import com.exchange.core.OrderBook
import com.exchange.model.Order
import com.exchange.sbe.Side
import com.exchange.sbe.OrderType

fun main() {
    val orderBook = OrderBook(1)
    val orderCount = 100_000
    
    println("--- JVM OrderBook Benchmark (100k Orders) ---")
    
    // Warm-up (Important for JVM/JIT)
    repeat(10_000) {
        val order = Order().apply { set(it.toLong(), 100, 50000, 1, Side.Buy, OrderType.Limit) }
        orderBook.processOrder(order) { _, _, _, _, _, _ -> }
    }
    
    val startTime = System.nanoTime()
    
    for (i in 1..orderCount) {
        val side = if (i % 2 == 0) Side.Buy else Side.Sell
        val price = if (side == Side.Buy) 50001L else 49999L
        
        val order = Order().apply { 
            set(i.toLong(), if (side == Side.Buy) 101 else 102, price, 1, side, OrderType.Limit) 
        }
        
        orderBook.processOrder(order) { _, _, _, _, _, _ -> }
    }
    
    val endTime = System.nanoTime()
    val totalTimeMs = (endTime - startTime) / 1_000_000.0
    
    println("Processed $orderCount orders in ${totalTimeMs}ms")
    println("Avg time per order: ${ (endTime - startTime) / orderCount }ns")
}
