package com.exchange

import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.util.DaemonThreadFactory
import com.lmax.disruptor.BusySpinWaitStrategy
import com.exchange.ipc.AeronSubscriber
import com.exchange.ipc.AeronEventPublisher
import io.aeron.Aeron
import io.aeron.driver.MediaDriver
import net.openhft.affinity.AffinityLock
import org.agrona.concurrent.BusySpinIdleStrategy
import org.agrona.concurrent.SigInt

import com.exchange.model.Order
import com.exchange.core.OrderBook
import com.exchange.sbe.Side
import com.exchange.sbe.OrderType
import com.lmax.disruptor.EventFactory
import com.lmax.disruptor.EventHandler

import com.exchange.sbe.ExecType

// 3. Handler
class MatchingEngineHandler(private val eventPublisher: com.exchange.ipc.AeronEventPublisher) : EventHandler<OrderEvent> {
    private val orderBooks = HashMap<Int, OrderBook>()
    private val tempOrder = Order()
    private var nextMatchId = 1L

    init {
        orderBooks[1] = OrderBook(1)
    }

    override fun onEvent(event: OrderEvent, sequence: Long, endOfBatch: Boolean) {
        try {
            if (event.type == 1) { // New Order
                // Debug Log
                println("ME: Received Order #${event.orderId} ${event.side} P=${event.price} Q=${event.qty}")
                
                val book = orderBooks[event.symbolId]
                if (book != null) {
                    tempOrder.set(
                        event.orderId, 
                        event.userId, 
                        event.price, 
                        event.qty, 
                        event.side, 
                        event.orderType
                    )
                    
                    book.processOrder(tempOrder) { makerId, takerId, makerUserId, takerUserId, price, qty ->
                        val matchId = nextMatchId++
                        println("MATCH: #$matchId | Price: $price | Qty: $qty | Maker: $makerId | Taker: $takerId")
                        eventPublisher.sendExecutionReport(
                            matchId,
                            makerId,
                            takerId,
                            makerUserId,
                            takerUserId,
                            price,
                            qty,
                            event.side,
                            ExecType.Trade
                        )
                    }

                    // Publish OrderBook Snapshot (Market Data)
                    val snapshot = book.getSnapshot()
                    eventPublisher.sendOrderBookSnapshot(
                        event.symbolId,
                        sequence,
                        snapshot.bidPrices, snapshot.bidQtys,
                        snapshot.askPrices, snapshot.askQtys
                    )
                }
            } else if (event.type == 2) { // Cancel Order
                println("ME: Cancel Request Order #${event.orderId}")
                val book = orderBooks[event.symbolId]
                if (book != null) {
                    val cancelledOrder = book.cancelOrder(event.orderId)
                    
                    if (cancelledOrder != null) {
                        println("ME: Order #${event.orderId} Cancelled. LeavesQty=${cancelledOrder.qty}")
                        eventPublisher.sendExecutionReport(
                            0, 
                            cancelledOrder.orderId,
                            0,
                            cancelledOrder.userId,
                            0, 
                            cancelledOrder.price,
                            cancelledOrder.qty, 
                            cancelledOrder.side,
                            ExecType.Cancel
                        )
                        
                        // Publish Snapshot Update
                        val snapshot = book.getSnapshot()
                        eventPublisher.sendOrderBookSnapshot(
                            event.symbolId,
                            sequence,
                            snapshot.bidPrices, snapshot.bidQtys,
                            snapshot.askPrices, snapshot.askQtys
                        )
                    } else {
                        println("ME: Order #${event.orderId} Not Found for Cancellation")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            event.clear()
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
