package com.exchange

import com.exchange.core.OrderBook
import com.exchange.ipc.AeronEventPublisher
import com.exchange.ipc.AeronSubscriber
import com.exchange.model.Order
import com.exchange.sbe.ExecType
import com.exchange.sbe.OrderType
import com.exchange.sbe.Side
import com.lmax.disruptor.BusySpinWaitStrategy
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.util.DaemonThreadFactory
import io.aeron.Aeron
import io.aeron.driver.MediaDriver
import net.openhft.affinity.AffinityLock
import org.agrona.concurrent.BusySpinIdleStrategy
import org.agrona.concurrent.SigInt

// 3. Handler
class MatchingEngineHandler(private val eventPublisher: com.exchange.ipc.AeronEventPublisher) : EventHandler<OrderEvent> {
    private val orderBooks = HashMap<Int, OrderBook>()
    private val stopOrders = HashMap<Int, MutableList<Order>>() // SymbolId -> List of Stop Orders
    private val tempOrder = Order()
    private var nextMatchId = 1L
    private var lastPrice = HashMap<Int, Long>() // SymbolId -> Last Traded Price

    init {
        orderBooks[1] = OrderBook(1)
        stopOrders[1] = ArrayList()
        lastPrice[1] = 0L
    }

    override fun onEvent(event: OrderEvent, sequence: Long, endOfBatch: Boolean) {
        try {
            if (event.type == 1) { // New Order
                val book = orderBooks[event.symbolId] ?: return
                
                // Case A: Stop Order
                if (event.orderType == OrderType.StopLimit || event.orderType == OrderType.StopMarket) {
                    println("ME: Stop Order Received #${event.orderId} Trigger=${event.triggerPrice}")
                    val stopOrder = Order()
                    stopOrder.set(event.orderId, event.userId, event.price, event.qty, event.side, event.orderType, event.triggerPrice)
                    stopOrders[event.symbolId]?.add(stopOrder)
                    return
                }

                // Case B: Normal Order (Limit/Market)
                processNormalOrder(event, book, sequence)
                
            } else if (event.type == 2) { // Cancel Order
                // ... cancel logic ...
                cancelOrder(event, sequence)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            event.clear()
        }
    }

    private fun processNormalOrder(event: OrderEvent, book: OrderBook, sequence: Long) {
        println("ME: Processing Order #${event.orderId} ${event.side} P=${event.price} Q=${event.qty}")
        
        tempOrder.set(
            event.orderId, 
            event.userId, 
            event.price, 
            event.qty, 
            event.side, 
            event.orderType,
            event.triggerPrice,
            event.tif
        )
        
        var matched = false
        book.processOrder(tempOrder) { makerId, takerId, makerUserId, takerUserId, price, qty ->
            matched = true
            val matchId = nextMatchId++
            lastPrice[event.symbolId] = price // Update Last Price
            
            println("MATCH: #$matchId | Price: $price | Qty: $qty | Maker: $makerId | Taker: $takerId")
            eventPublisher.sendExecutionReport(
                matchId, makerId, takerId, makerUserId, takerUserId, price, qty, event.side, ExecType.Trade
            )
        }

        // Check for Stop Order Triggers after matching or resting
        if (matched) {
            checkTriggers(event.symbolId, sequence)
        }

        // Publish Snapshot
        val snapshot = book.getSnapshot()
        eventPublisher.sendOrderBookSnapshot(
            event.symbolId, sequence,
            snapshot.bidPrices, snapshot.bidQtys,
            snapshot.askPrices, snapshot.askQtys
        )
    }

    private fun checkTriggers(symbolId: Int, sequence: Long) {
        val currentPrice = lastPrice[symbolId] ?: return
        if (currentPrice == 0L) return

        val stops = stopOrders[symbolId] ?: return
        val iter = stops.iterator()
        
        while (iter.hasNext()) {
            val stop = iter.next()
            var triggered = false
            
            // Trigger Logic:
            // Buy Stop: Trigger if Price >= TriggerPrice
            // Sell Stop: Trigger if Price <= TriggerPrice
            if (stop.side == Side.Buy && currentPrice >= stop.triggerPrice) triggered = true
            if (stop.side == Side.Sell && currentPrice <= stop.triggerPrice) triggered = true
            
            if (triggered) {
                println("ME: STOP TRIGGERED! Order #${stop.orderId} at Price $currentPrice")
                iter.remove() // Remove from stop book
                
                // Notify Triggered (Optional but good for status)
                eventPublisher.sendExecutionReport(
                    0, stop.orderId, 0, stop.userId, 0, 0, 0, stop.side, ExecType.Triggered
                )

                // Convert to Normal Order and Process
                // StopMarket becomes Market, StopLimit becomes Limit
                val newType = if (stop.type == OrderType.StopMarket) OrderType.Market else OrderType.Limit
                
                val book = orderBooks[symbolId]!!
                val triggeredOrder = Order()
                triggeredOrder.set(stop.orderId, stop.userId, stop.price, stop.qty, stop.side, newType)
                
                book.processOrder(triggeredOrder) { makerId, takerId, makerUserId, takerUserId, price, qty ->
                    val matchId = nextMatchId++
                    lastPrice[symbolId] = price
                    eventPublisher.sendExecutionReport(
                        matchId, makerId, takerId, makerUserId, takerUserId, price, qty, stop.side, ExecType.Trade
                    )
                }
            }
        }
    }

    private fun cancelOrder(event: OrderEvent, sequence: Long) {
        println("ME: Cancel Request Order #${event.orderId}")
        val book = orderBooks[event.symbolId]
        if (book != null) {
            val cancelledOrder = book.cancelOrder(event.orderId)
            
            if (cancelledOrder != null) {
                println("ME: Order #${event.orderId} Cancelled. LeavesQty=${cancelledOrder.qty}")
                eventPublisher.sendExecutionReport(
                    0, cancelledOrder.orderId, 0, cancelledOrder.userId, 0, cancelledOrder.price, cancelledOrder.qty, cancelledOrder.side, ExecType.Cancel
                )
                
                val snapshot = book.getSnapshot()
                eventPublisher.sendOrderBookSnapshot(
                    event.symbolId, sequence,
                    snapshot.bidPrices, snapshot.bidQtys,
                    snapshot.askPrices, snapshot.askQtys
                )
            } else {
                // Also check Stop Orders for cancellation
                val stops = stopOrders[event.symbolId]
                val stopIter = stops?.iterator()
                while (stopIter?.hasNext() == true) {
                    val s = stopIter.next()
                    if (s.orderId == event.orderId) {
                        println("ME: Stop Order #${event.orderId} Cancelled (from StopBook)")
                        stopIter.remove()
                        eventPublisher.sendExecutionReport(
                            0, s.orderId, 0, s.userId, 0, s.price, s.qty, s.side, ExecType.Cancel
                        )
                        return
                    }
                }
                println("ME: Order #${event.orderId} Not Found for Cancellation")
            }
        }
    }
}

fun main(args: Array<String>) {
    println("Starting Matching Engine Server...")

    // 1. Configuration (Could be loaded from Env/Args)
    // In production, MediaDriver might be external (e.g. Aeron Cluster or standalone driver)
    // For standalone deployment, we launch embedded if not connecting to existing.
    val launchEmbeddedDriver = args.contains("--embedded-driver")
    
    val driver = if (launchEmbeddedDriver) {
        println("Launching Embedded Media Driver...")
        MediaDriver.launchEmbedded()
    } else {
        null
    }
    
    val aeronDir = driver?.aeronDirectoryName() ?: System.getProperty("aeron.dir") ?: io.aeron.CommonContext.getAeronDirectoryName()
    println("Connecting to Aeron at: $aeronDir")
    
    val ctx = Aeron.Context().aeronDirectoryName(aeronDir)
    val aeron = Aeron.connect(ctx)

    // 2. CPU Affinity
    try {
        AffinityLock.acquireLock()
        println("CPU Affinity Locked.")
    } catch (e: Throwable) {
        println("Warning: Could not acquire CPU Affinity: ${e.message}")
    }

    // 3. Components
    val eventPublisher = AeronEventPublisher(aeron)
    val factory = OrderEventFactory()
    val bufferSize = 1024 * 64 // 2^16
    
    val disruptor = Disruptor(
        factory, 
        bufferSize, 
        DaemonThreadFactory.INSTANCE,
        com.lmax.disruptor.dsl.ProducerType.MULTI,
        BusySpinWaitStrategy()
    )

    // Wire Handler
    disruptor.handleEventsWith(MatchingEngineHandler(eventPublisher))
    disruptor.start()
    
    // 4. Input Subscriber
    val subscriber = AeronSubscriber(aeron, disruptor.ringBuffer, com.exchange.ipc.ExchangeConstants.ENGINE_STREAM_ID)
    
    println("Matching Engine Started. Listening for orders...")
    
    SigInt.register {
        println("Shutting down...")
        disruptor.shutdown()
        aeron.close()
        driver?.close()
    }

    val idleStrategy = BusySpinIdleStrategy()

    // 5. Main Loop
    while (true) {
        val fragmentsRead = subscriber.poll(10)
        idleStrategy.idle(fragmentsRead)
    }
}
