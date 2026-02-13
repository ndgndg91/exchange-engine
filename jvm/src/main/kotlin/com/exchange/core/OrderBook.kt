package com.exchange.core

import com.exchange.model.Order
import com.exchange.sbe.Side
import com.exchange.sbe.OrderType
import com.exchange.sbe.TimeInForce
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap
import org.eclipse.collections.api.list.MutableList
import org.eclipse.collections.impl.factory.Lists
import java.util.ArrayDeque

data class SnapshotData(
    val bidPrices: LongArray, val bidQtys: LongArray,
    val askPrices: LongArray, val askQtys: LongArray
)

class OrderBook(val symbolId: Int) {
    // Price -> List of Orders. 
    private val bids = LongObjectHashMap<MutableList<Order>>()
    private val asks = LongObjectHashMap<MutableList<Order>>()
    
    // O(1) Index for Cancellations: OrderId -> Order
    private val orderIndex = LongObjectHashMap<Order>()
    
    // Object Pool to prevent 'new Order()' during runtime
    private val orderPool = ArrayDeque<Order>(1_000_000)

    init {
        // Pre-allocate pool
        repeat(1_000_000) {
            orderPool.add(Order())
        }
    }

    private fun borrowOrder(): Order {
        return if (orderPool.isEmpty()) {
            println("WARN: OrderPool empty, allocating new Order!") 
            Order() 
        } else {
            orderPool.pollFirst()
        }
    }

    private fun returnOrder(order: Order) {
        orderIndex.remove(order.orderId) // Remove from index
        order.clear()
        orderPool.addLast(order)
    }

    fun getBestBid(): Long = if (bids.isEmpty) 0L else bids.keySet().max()
    fun getBestAsk(): Long = if (asks.isEmpty) 0L else asks.keySet().min()

    fun processOrder(newOrder: Order, onMatch: (Long, Long, Long, Long, Long, Long) -> Unit) {
        if (newOrder.side == Side.Buy) {
            match(newOrder, asks, bids, onMatch) { makerPrice, takerPrice -> makerPrice <= takerPrice }
        } else {
            match(newOrder, bids, asks, onMatch) { makerPrice, takerPrice -> makerPrice >= takerPrice }
        }
    }
    
    // New Method: Cancel Order
    // Returns cancelled Order object (copy) or null if not found
    fun cancelOrder(orderId: Long): Order? {
        val order = orderIndex.get(orderId) ?: return null
        
        // Remove from Book (Price Level List)
        val book = if (order.side == Side.Buy) bids else asks
        val ordersAtLevel = book.get(order.price)
        
        if (ordersAtLevel != null) {
            ordersAtLevel.remove(order) // O(N) of list size (usually small)
            if (ordersAtLevel.isEmpty) {
                book.remove(order.price)
            }
        }
        
        // Copy to return before clearing
        val cancelledOrder = Order()
        cancelledOrder.set(order.orderId, order.userId, order.price, order.qty, order.side, order.type, 0, order.tif)
        
        // Return to pool
        returnOrder(order)
        
        return cancelledOrder
    }

    // Inline function for performance
    private inline fun match(
        taker: Order,
        opposingBook: LongObjectHashMap<MutableList<Order>>,
        myBook: LongObjectHashMap<MutableList<Order>>,
        noinline onMatch: (Long, Long, Long, Long, Long, Long) -> Unit,
        priceCheck: (Long, Long) -> Boolean
    ) {
        val sortedPrices = opposingBook.keySet().toSortedList()
        if (taker.side != Side.Buy) {
            sortedPrices.reverseThis()
        }

        val priceIter = sortedPrices.longIterator()

        while (priceIter.hasNext() && taker.qty > 0) {
            val bestPrice = priceIter.next()
            
            // Market Order: Ignore Price Check
            // Limit Order: Check Price
            if (taker.type != OrderType.Market && !priceCheck(bestPrice, taker.price)) break

            val ordersAtLevel = opposingBook.get(bestPrice)
            val orderIter = ordersAtLevel.iterator()

            while (orderIter.hasNext() && taker.qty > 0) {
                val maker = orderIter.next()
                
                // Self-Trade Protection
                if (maker.userId == taker.userId) {
                    orderIter.remove()
                    returnOrder(maker) // Handles index removal
                    continue
                }

                val tradeQty = Math.min(maker.qty, taker.qty)
                // EXECUTION EVENT: MakerId, TakerId, MakerUserId, TakerUserId, Price, Qty
                onMatch(maker.orderId, taker.orderId, maker.userId, taker.userId, bestPrice, tradeQty)

                maker.qty -= tradeQty
                taker.qty -= tradeQty

                if (maker.qty == 0L) {
                    orderIter.remove()
                    returnOrder(maker) // Handles index removal
                }
            }

            if (ordersAtLevel.isEmpty) {
                opposingBook.remove(bestPrice)
            }
        }

        // Rest in book (Maker) - ONLY if Limit Order AND NOT IOC
        // Market Order = IOC behavior
        if (taker.qty > 0 && taker.type != OrderType.Market && taker.tif != TimeInForce.IOC) {
            val bookOrder = borrowOrder()
            bookOrder.set(taker.orderId, taker.userId, taker.price, taker.qty, taker.side, taker.type, 0, taker.tif)
            
            var list = myBook.get(taker.price)
            if (list == null) {
                list = Lists.mutable.empty()
                myBook.put(taker.price, list)
            }
            list.add(bookOrder)
            
            // Add to Index
            orderIndex.put(bookOrder.orderId, bookOrder)
        }
    }

    fun getSnapshot(): SnapshotData {
        val bidPrices = LongArray(5)
        val bidQtys = LongArray(5)
        val askPrices = LongArray(5)
        val askQtys = LongArray(5)

        val sortedBids = bids.keySet().toSortedList()
        for (i in 0 until 5) {
            if (i >= sortedBids.size()) break
            val price = sortedBids.get(sortedBids.size() - 1 - i)
            val orders = bids.get(price)
            var totalQty = 0L
            orders.forEach { totalQty += it.qty }
            bidPrices[i] = price
            bidQtys[i] = totalQty
        }

        val sortedAsks = asks.keySet().toSortedList()
        for (i in 0 until 5) {
            if (i >= sortedAsks.size()) break
            val price = sortedAsks.get(i)
            val orders = asks.get(price)
            var totalQty = 0L
            orders.forEach { totalQty += it.qty }
            askPrices[i] = price
            askQtys[i] = totalQty
        }

        return SnapshotData(bidPrices, bidQtys, askPrices, askQtys)
    }
}