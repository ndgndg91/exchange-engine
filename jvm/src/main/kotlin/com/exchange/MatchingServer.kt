package com.exchange

import com.exchange.core.OrderBook
import com.exchange.ipc.AeronEventPublisher
import com.exchange.ipc.AeronSubscriber
import com.exchange.model.Order
import com.exchange.sbe.ExecType
import com.exchange.sbe.Side
import com.exchange.sbe.OrderType
import com.exchange.sbe.TimeInForce
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.util.DaemonThreadFactory
import com.lmax.disruptor.BusySpinWaitStrategy
import io.aeron.Aeron
import io.aeron.driver.MediaDriver
import org.agrona.concurrent.BusySpinIdleStrategy
import org.agrona.concurrent.SigInt
import net.openhft.affinity.AffinityLock
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap
import org.eclipse.collections.impl.map.mutable.primitive.IntLongHashMap
import java.util.ArrayList

class MatchingEngineHandler(private val eventPublisher: AeronEventPublisher) : EventHandler<OrderEvent> {
    private val orderBooks = IntObjectHashMap<OrderBook>()
    private val stopOrders = IntObjectHashMap<MutableList<Order>>()
    private val lastPrice = IntLongHashMap()
    private var nextMatchId = 1L
    
    // Reusable Order object for matching logic
    private val tempOrder = Order()

    init {
        // Initialize Symbols (Prototype: only symbol 1)
        orderBooks.put(1, OrderBook(1))
        stopOrders.put(1, ArrayList())
    }

    override fun onEvent(event: OrderEvent, sequence: Long, endOfBatch: Boolean) {
        try {
            if (event.type == 1) { // New Order
                val book = orderBooks[event.symbolId]
                if (book == null) return

                if (event.orderType == OrderType.StopLimit || event.orderType == OrderType.StopMarket) {
                    println("ME: Registered STOP Order #${event.orderId} Trigger=${event.triggerPrice}")
                    val stops = stopOrders[event.symbolId] ?: ArrayList<Order>().also { stopOrders.put(event.symbolId, it) }
                    val stopOrder = Order()
                    stopOrder.set(event.orderId, event.userId, event.price, event.qty, event.side, event.orderType, event.triggerPrice, event.tif)
                    stops.add(stopOrder)
                    
                    // Notify Accepted (Stop)
                    eventPublisher.sendExecutionReport(
                        0, event.orderId, 0, event.userId, 0, event.price, event.qty, event.side, ExecType.NULL_VAL
                    )
                } else {
                    processNormalOrder(event, book, sequence)
                }
                
            } else if (event.type == 2) { // Cancel Order
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
            lastPrice.put(event.symbolId, price) // Update Last Price
            
            println("MATCH: #$matchId | Price: $price | Qty: $qty | Maker: $makerId | Taker: $takerId")
            eventPublisher.sendExecutionReport(
                matchId, makerId, takerId, makerUserId, takerUserId, price, qty, event.side, ExecType.Trade
            )
        }

        // Check for Stop Order Triggers after matching or resting
        if (matched) {
            checkTriggers(event.symbolId, sequence)
        }

        // IOC / FOK / Market Order Cancellation (Unfilled portion)
        if (tempOrder.qty > 0 && (tempOrder.type == OrderType.Market || tempOrder.tif == TimeInForce.IOC || tempOrder.tif == TimeInForce.FOK)) {
            println("ME: IOC/FOK/Market Unfilled Part Cancelled: ${tempOrder.qty} for Order #${tempOrder.orderId}")
            eventPublisher.sendExecutionReport(
                0, tempOrder.orderId, 0, tempOrder.userId, 0, tempOrder.price, tempOrder.qty, tempOrder.side, ExecType.Cancel
            )
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
        val currentPrice = lastPrice.get(symbolId)
        if (currentPrice == 0L) return

        val stops = stopOrders[symbolId] ?: return
        val iter = stops.iterator()
        
        while (iter.hasNext()) {
            val stop = iter.next()
            var triggered = false
            
            if (stop.side == Side.Buy && currentPrice >= stop.triggerPrice) triggered = true
            if (stop.side == Side.Sell && currentPrice <= stop.triggerPrice) triggered = true
            
            if (triggered) {
                println("ME: STOP TRIGGERED! Order #${stop.orderId} at Price $currentPrice")
                iter.remove() // Remove from stop book
                
                eventPublisher.sendExecutionReport(
                    0, stop.orderId, 0, stop.userId, 0, 0, 0, stop.side, ExecType.Triggered
                )

                val newType = if (stop.type == OrderType.StopMarket) OrderType.Market else OrderType.Limit
                
                val book = orderBooks[symbolId]!!
                val triggeredOrder = Order()
                triggeredOrder.set(stop.orderId, stop.userId, stop.price, stop.qty, stop.side, newType, 0, stop.tif)
                
                book.processOrder(triggeredOrder) { makerId, takerId, makerUserId, takerUserId, price, qty ->
                    val matchId = nextMatchId++
                    lastPrice.put(symbolId, price)
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
            val cancelled = book.cancelOrder(event.orderId)
            if (cancelled != null) {
                println("ME: Order #${event.orderId} Cancelled Successfully")
                eventPublisher.sendExecutionReport(
                    0, cancelled.orderId, 0, cancelled.userId, 0, cancelled.price, cancelled.qty, cancelled.side, ExecType.Cancel
                )
            } else {
                println("ME: Order #${event.orderId} Not Found for Cancellation")
            }
        }
    }
}

fun main(args: Array<String>) {
    println("Starting Matching Engine Server...")

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

    try {
        AffinityLock.acquireLock()
        println("CPU Affinity Locked.")
    } catch (e: Throwable) {
        println("Warning: Could not acquire CPU Affinity: ${e.message}")
    }

    val eventPublisher = AeronEventPublisher(aeron)
    val factory = OrderEventFactory()
    val bufferSize = 1024 * 64
    
    val disruptor = Disruptor(
        factory, 
        bufferSize, 
        DaemonThreadFactory.INSTANCE,
        com.lmax.disruptor.dsl.ProducerType.MULTI,
        BusySpinWaitStrategy()
    )

    disruptor.handleEventsWith(MatchingEngineHandler(eventPublisher))
    disruptor.start()
    
    val subscriber = AeronSubscriber(aeron, disruptor.ringBuffer, com.exchange.ipc.ExchangeConstants.ENGINE_STREAM_ID)
    
    println("Matching Engine Started. Listening for orders...")
    
    SigInt.register {
        println("Shutting down...")
        disruptor.shutdown()
        aeron.close()
        driver?.close()
    }

    val idleStrategy = BusySpinIdleStrategy()

    while (true) {
        val fragmentsRead = subscriber.poll(10)
        idleStrategy.idle(fragmentsRead)
    }
}
