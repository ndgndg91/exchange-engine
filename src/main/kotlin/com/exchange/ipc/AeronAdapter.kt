package com.exchange.ipc

import com.exchange.OrderEvent
import com.exchange.sbe.*
import com.lmax.disruptor.RingBuffer
import io.aeron.Aeron
import io.aeron.ConcurrentPublication
import io.aeron.Subscription
import io.aeron.logbuffer.FragmentHandler
import org.agrona.concurrent.BusySpinIdleStrategy
import org.agrona.concurrent.UnsafeBuffer
import java.nio.ByteBuffer

object ExchangeConstants {
    // IPC Channel for local testing (fastest)
    // For network: "aeron:udp?endpoint=224.0.1.1:40456"
    const val CHANNEL = "aeron:ipc" 
    const val STREAM_ID = 10       // Gateway -> OME (Input Commands)
    const val ENGINE_STREAM_ID = 11 // OME -> ME (Validated Orders)
    const val EVENT_STREAM_ID = 20 // ME -> OME/Gateway (Execution Reports)
}

/**
 * Handles sending messages via Aeron (Used by OME)
 */
class AeronPublisher(private val aeron: Aeron) {
    private val publication: ConcurrentPublication = aeron.addPublication(ExchangeConstants.CHANNEL, ExchangeConstants.STREAM_ID)
    private val enginePublication: ConcurrentPublication = aeron.addPublication(ExchangeConstants.CHANNEL, ExchangeConstants.ENGINE_STREAM_ID)
    private val buffer = UnsafeBuffer(ByteBuffer.allocateDirect(1024))
    private val headerEncoder = MessageHeaderEncoder()
    private val newOrderEncoder = NewOrderSingleEncoder()
    private val cancelEncoder = OrderCancelEncoder()

    fun sendCancel(userId: Long, orderId: Long, symbolId: Int, seqId: Long) {
        while (!publication.isConnected) {
            Thread.yield()
        }

        headerEncoder.wrap(buffer, 0)
            .blockLength(cancelEncoder.sbeBlockLength())
            .templateId(cancelEncoder.sbeTemplateId())
            .schemaId(cancelEncoder.sbeSchemaId())
            .version(cancelEncoder.sbeSchemaVersion())

        cancelEncoder.wrap(buffer, headerEncoder.encodedLength())
            .userId(userId)
            .origOrderId(orderId)
            .symbolId(symbolId)
            .seqId(seqId)

        val length = headerEncoder.encodedLength() + cancelEncoder.encodedLength()

        var result: Long
        val idle = BusySpinIdleStrategy()
        
        while (publication.offer(buffer, 0, length).also { result = it } < 0L) {
            if (result == io.aeron.Publication.CLOSED) throw IllegalStateException("Connection closed")
            idle.idle()
        }
    }

    fun sendOrderToEngine(userId: Long, symbolId: Int, price: Long, qty: Long, side: Side, type: OrderType, seqId: Long, triggerPrice: Long = 0, tif: TimeInForce = TimeInForce.GTC) {
        while (!enginePublication.isConnected) {
            Thread.yield()
        }

        headerEncoder.wrap(buffer, 0)
            .blockLength(newOrderEncoder.sbeBlockLength())
            .templateId(newOrderEncoder.sbeTemplateId())
            .schemaId(newOrderEncoder.sbeSchemaId())
            .version(newOrderEncoder.sbeSchemaVersion())

        newOrderEncoder.wrap(buffer, headerEncoder.encodedLength())
            .userId(userId)
            .symbolId(symbolId)
            .price(price)
            .qty(qty)
            .side(side)
            .seqId(seqId)
            .orderType(type)
            .triggerPrice(triggerPrice)
            .tif(tif)

        val length = headerEncoder.encodedLength() + newOrderEncoder.encodedLength()

        var result: Long
        val idle = BusySpinIdleStrategy()
        
        while (enginePublication.offer(buffer, 0, length).also { result = it } < 0L) {
            if (result == io.aeron.Publication.CLOSED) throw IllegalStateException("Connection closed")
            idle.idle()
        }
    }

    fun sendCancelToEngine(userId: Long, orderId: Long, symbolId: Int, seqId: Long) {
        while (!enginePublication.isConnected) {
            Thread.yield()
        }

        headerEncoder.wrap(buffer, 0)
            .blockLength(cancelEncoder.sbeBlockLength())
            .templateId(cancelEncoder.sbeTemplateId())
            .schemaId(cancelEncoder.sbeSchemaId())
            .version(cancelEncoder.sbeSchemaVersion())

        cancelEncoder.wrap(buffer, headerEncoder.encodedLength())
            .userId(userId)
            .origOrderId(orderId)
            .symbolId(symbolId)
            .seqId(seqId)

        val length = headerEncoder.encodedLength() + cancelEncoder.encodedLength()

        var result: Long
        val idle = BusySpinIdleStrategy()
        
        while (enginePublication.offer(buffer, 0, length).also { result = it } < 0L) {
            if (result == io.aeron.Publication.CLOSED) throw IllegalStateException("Connection closed")
            idle.idle()
        }
    }

    fun sendOrder(userId: Long, symbolId: Int, price: Long, qty: Long, side: Side, type: OrderType, seqId: Long, triggerPrice: Long = 0, tif: TimeInForce = TimeInForce.GTC) {
        // Wait for connection
        while (!publication.isConnected) {
            Thread.yield()
        }

        // 1. Encode Header
        headerEncoder.wrap(buffer, 0)
            .blockLength(newOrderEncoder.sbeBlockLength())
            .templateId(newOrderEncoder.sbeTemplateId())
            .schemaId(newOrderEncoder.sbeSchemaId())
            .version(newOrderEncoder.sbeSchemaVersion())

        // 2. Encode Body
        newOrderEncoder.wrap(buffer, headerEncoder.encodedLength())
            .userId(userId)
            .symbolId(symbolId)
            .price(price)
            .qty(qty)
            .side(side)
            .seqId(seqId)
            .orderType(type)
            .triggerPrice(triggerPrice)
            .tif(tif)

        val length = headerEncoder.encodedLength() + newOrderEncoder.encodedLength()

        // 3. Send (Retry loop for backpressure)
        var result: Long
        val idle = BusySpinIdleStrategy()
        
        while (publication.offer(buffer, 0, length).also { result = it } < 0L) {
            // handle backpressure (NOT_CONNECTED, ADMIN_ACTION, BACK_PRESSURE)
            if (result == io.aeron.Publication.CLOSED) throw IllegalStateException("Connection closed")
            idle.idle()
        }
    }

    fun sendBuffer(srcBuffer: org.agrona.DirectBuffer, offset: Int, length: Int) {
        // Wait for connection
        while (!publication.isConnected) {
            Thread.yield()
        }
        
        var result: Long
        val idle = BusySpinIdleStrategy()
        
        while (publication.offer(srcBuffer, offset, length).also { result = it } < 0L) {
            if (result == io.aeron.Publication.CLOSED) throw IllegalStateException("Connection closed")
            idle.idle()
        }
    }
}

/**
 * Handles sending Events (Execution Reports) via Aeron (Used by ME)
 */
class AeronEventPublisher(private val aeron: Aeron) {
    private val publication: ConcurrentPublication = aeron.addPublication(ExchangeConstants.CHANNEL, ExchangeConstants.EVENT_STREAM_ID)
    private val buffer = UnsafeBuffer(ByteBuffer.allocateDirect(1024))
    private val headerEncoder = MessageHeaderEncoder()
    private val execReportEncoder = ExecutionReportEncoder()
    private val snapshotEncoder = OrderBookSnapshotEncoder()

    fun sendOrderBookSnapshot(
        symbolId: Int, seqId: Long,
        bidPrices: LongArray, bidQtys: LongArray,
        askPrices: LongArray, askQtys: LongArray
    ) {
         // Wait for connection
        if (!publication.isConnected) {
             return // Non-blocking: skip if no subscribers to avoid stalling ME
        }

        // 1. Encode Header
        headerEncoder.wrap(buffer, 0)
            .blockLength(snapshotEncoder.sbeBlockLength())
            .templateId(snapshotEncoder.sbeTemplateId())
            .schemaId(snapshotEncoder.sbeSchemaId())
            .version(snapshotEncoder.sbeSchemaVersion())

        // 2. Encode Body
        snapshotEncoder.wrap(buffer, headerEncoder.encodedLength())
            .symbolId(symbolId)
            .seqId(seqId)
            // Bids
            .bidPrice0(bidPrices[0]).bidQty0(bidQtys[0])
            .bidPrice1(bidPrices[1]).bidQty1(bidQtys[1])
            .bidPrice2(bidPrices[2]).bidQty2(bidQtys[2])
            .bidPrice3(bidPrices[3]).bidQty3(bidQtys[3])
            .bidPrice4(bidPrices[4]).bidQty4(bidQtys[4])
            // Asks
            .askPrice0(askPrices[0]).askQty0(askQtys[0])
            .askPrice1(askPrices[1]).askQty1(askQtys[1])
            .askPrice2(askPrices[2]).askQty2(askQtys[2])
            .askPrice3(askPrices[3]).askQty3(askQtys[3])
            .askPrice4(askPrices[4]).askQty4(askQtys[4])

        val length = headerEncoder.encodedLength() + snapshotEncoder.encodedLength()

        // 3. Send
        var result: Long
        val idle = BusySpinIdleStrategy()
        
        while (publication.offer(buffer, 0, length).also { result = it } < 0L) {
            if (result == io.aeron.Publication.CLOSED) throw IllegalStateException("Connection closed")
            idle.idle()
        }
    }

    fun sendExecutionReport(matchId: Long, makerOrderId: Long, takerOrderId: Long, makerUserId: Long, takerUserId: Long, price: Long, qty: Long, side: Side, execType: ExecType) {
         // Wait for connection (Blocking for simplicity in prototype)
        while (!publication.isConnected) {
            Thread.yield()
        }

        // 1. Encode
        headerEncoder.wrap(buffer, 0)
            .blockLength(execReportEncoder.sbeBlockLength())
            .templateId(execReportEncoder.sbeTemplateId())
            .schemaId(execReportEncoder.sbeSchemaId())
            .version(execReportEncoder.sbeSchemaVersion())

        execReportEncoder.wrap(buffer, headerEncoder.encodedLength())
            .matchId(matchId)
            .makerOrderId(makerOrderId)
            .takerOrderId(takerOrderId)
            .makerUserId(makerUserId)
            .takerUserId(takerUserId)
            .price(price)
            .qty(qty)
            .side(side)
            .execType(execType)

        val length = headerEncoder.encodedLength() + execReportEncoder.encodedLength()

        // 2. Send
        var result: Long
        val idle = BusySpinIdleStrategy()
        
        while (publication.offer(buffer, 0, length).also { result = it } < 0L) {
            if (result == io.aeron.Publication.CLOSED) throw IllegalStateException("Connection closed")
            idle.idle()
        }
    }
}

/**
 * Handles receiving messages from Aeron and pushing to Disruptor (Used by ME)
 */
class AeronSubscriber(
    private val aeron: Aeron, 
    private val disruptorRingBuffer: RingBuffer<OrderEvent>,
    streamId: Int
) {
    private val subscription: Subscription = aeron.addSubscription(ExchangeConstants.CHANNEL, streamId)
    private val headerDecoder = MessageHeaderDecoder()
    private val newOrderDecoder = NewOrderSingleDecoder()
    private val cancelDecoder = OrderCancelDecoder()

    // Callback called by Aeron for every packet
    private val fragmentHandler = FragmentHandler { buffer, offset, length, header ->
        // 1. Decode Header
        headerDecoder.wrap(buffer, offset)
        
        val templateId = headerDecoder.templateId()
        val actingBlockLength = headerDecoder.blockLength()
        val actingVersion = headerDecoder.version()
        val bodyOffset = offset + headerDecoder.encodedLength()

        // 2. Dispatch based on Template ID
        if (templateId == NewOrderSingleEncoder.TEMPLATE_ID) {
            newOrderDecoder.wrap(buffer, bodyOffset, actingBlockLength, actingVersion)
            
            // 3. Publish to Disruptor (Zero-Copy-ish: we extract fields to primitive vars)
            val seq = disruptorRingBuffer.next()
            try {
                val event = disruptorRingBuffer.get(seq)
                event.type = 1 // NewOrder
                event.orderId = newOrderDecoder.seqId() // Map seqId to OrderId
                event.userId = newOrderDecoder.userId()
                event.symbolId = newOrderDecoder.symbolId()
                event.price = newOrderDecoder.price()
                event.qty = newOrderDecoder.qty()
                event.side = newOrderDecoder.side()
                event.orderType = newOrderDecoder.orderType()
                event.triggerPrice = newOrderDecoder.triggerPrice()
                event.tif = newOrderDecoder.tif()
            } finally {
                disruptorRingBuffer.publish(seq)
            }
        } else if (templateId == OrderCancelEncoder.TEMPLATE_ID) {
            cancelDecoder.wrap(buffer, bodyOffset, actingBlockLength, actingVersion)
            
            val seq = disruptorRingBuffer.next()
            try {
                val event = disruptorRingBuffer.get(seq)
                event.type = 2 // Cancel
                event.orderId = cancelDecoder.origOrderId() // Target Order ID to cancel
                event.userId = cancelDecoder.userId()
                event.symbolId = cancelDecoder.symbolId()
            } finally {
                disruptorRingBuffer.publish(seq)
            }
        }
    }

    fun poll(limit: Int): Int {
        return subscription.poll(fragmentHandler, limit)
    }
}

/**
 * Handles receiving Events (Execution Reports) from Aeron (Used by OME)
 */
class AeronEventSubscriber(
    private val aeron: Aeron,
    private val onExecutionReport: (Long, Long, Long, Long, Side, Long, Long, ExecType) -> Unit // Added UserIDs and ExecType
) {
    private val subscription: Subscription = aeron.addSubscription(ExchangeConstants.CHANNEL, ExchangeConstants.EVENT_STREAM_ID)
    private val headerDecoder = MessageHeaderDecoder()
    private val execReportDecoder = ExecutionReportDecoder()

    private val fragmentHandler = FragmentHandler { buffer, offset, length, header ->
        headerDecoder.wrap(buffer, offset)
        
        val templateId = headerDecoder.templateId()
        val actingBlockLength = headerDecoder.blockLength()
        val actingVersion = headerDecoder.version()
        val bodyOffset = offset + headerDecoder.encodedLength()

        if (templateId == ExecutionReportDecoder.TEMPLATE_ID) {
            execReportDecoder.wrap(buffer, bodyOffset, actingBlockLength, actingVersion)
            
            // Invoke callback (OME Logic)
            onExecutionReport(
                execReportDecoder.makerOrderId(),
                execReportDecoder.takerOrderId(),
                execReportDecoder.makerUserId(),
                execReportDecoder.takerUserId(),
                execReportDecoder.side(),
                execReportDecoder.price(),
                execReportDecoder.qty(),
                execReportDecoder.execType()
            )
        }
    }

    fun poll(limit: Int): Int {
        return subscription.poll(fragmentHandler, limit)
    }
}