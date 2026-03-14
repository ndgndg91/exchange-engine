package com.exchange.ipc

import com.exchange.OrderEvent
import com.exchange.sbe.*
import com.lmax.disruptor.RingBuffer
import io.aeron.Aeron
import io.aeron.ConcurrentPublication
import io.aeron.Subscription
import io.aeron.exceptions.RegistrationException
import io.aeron.logbuffer.FragmentHandler
import org.agrona.concurrent.BusySpinIdleStrategy
import org.agrona.concurrent.UnsafeBuffer
import java.nio.ByteBuffer

object ExchangeConstants {
    val PUB_CHANNEL: String = System.getProperty("AERON_PUB_CHANNEL") ?: System.getenv("AERON_PUB_CHANNEL") ?: "aeron:udp?endpoint=localhost:40123"
    val SUB_CHANNEL: String = System.getProperty("AERON_SUB_CHANNEL") ?: System.getenv("AERON_SUB_CHANNEL") ?: "aeron:udp?endpoint=0.0.0.0:40123"
    const val CHANNEL = "aeron:ipc"
    const val STREAM_ID = 10
    const val ENGINE_STREAM_ID = 11
    const val EVENT_STREAM_ID = 20

    fun <T> retryAeronAction(name: String, action: () -> T): T {
        var attempts = 0
        while (attempts < 60) {
            try { return action() } 
            catch (e: Throwable) {
                System.err.println("!!!! AERON_RETRY: $name failed. Cause: ${e.message} !!!!")
                Thread.sleep(2000)
                attempts++
            }
        }
        throw IllegalStateException("Aeron $name failed after max retries")
    }

    fun toListenChannel(channel: String): String {
        if (!channel.contains("udp")) return channel
        return channel.replace(Regex("endpoint=[^:]+"), "endpoint=0.0.0.0")
    }
}

class AeronPublisher(private val aeron: Aeron) {
    private val publication = ExchangeConstants.retryAeronAction("InputPub") { aeron.addPublication(ExchangeConstants.SUB_CHANNEL, ExchangeConstants.STREAM_ID) }
    private val enginePublication = ExchangeConstants.retryAeronAction("EnginePub") { aeron.addPublication(ExchangeConstants.PUB_CHANNEL, ExchangeConstants.ENGINE_STREAM_ID) }
    
    private val bufferTl = ThreadLocal.withInitial { UnsafeBuffer(ByteBuffer.allocateDirect(1024)) }
    private val headerEncoderTl = ThreadLocal.withInitial { MessageHeaderEncoder() }
    private val newOrderEncoderTl = ThreadLocal.withInitial { NewOrderSingleEncoder() }
    private val cancelEncoderTl = ThreadLocal.withInitial { OrderCancelEncoder() }

    fun sendOrder(userId: Long, symbolId: Int, price: Long, qty: Long, side: Side, type: OrderType, seqId: Long, triggerPrice: Long = 0, tif: TimeInForce = TimeInForce.GTC) {
        publishOrder(publication, userId, symbolId, price, qty, side, type, seqId, triggerPrice, tif)
    }
    fun sendOrderToEngine(userId: Long, symbolId: Int, price: Long, qty: Long, side: Side, type: OrderType, seqId: Long, triggerPrice: Long = 0, tif: TimeInForce = TimeInForce.GTC) {
        publishOrder(enginePublication, userId, symbolId, price, qty, side, type, seqId, triggerPrice, tif)
    }
    fun sendCancel(userId: Long, orderId: Long, symbolId: Int, seqId: Long) {
        publishCancel(publication, userId, orderId, symbolId, seqId)
    }
    fun sendCancelToEngine(userId: Long, orderId: Long, symbolId: Int, seqId: Long) {
        publishCancel(enginePublication, userId, orderId, symbolId, seqId)
    }
    private fun publishOrder(pub: ConcurrentPublication, userId: Long, symbolId: Int, price: Long, qty: Long, side: Side, type: OrderType, seqId: Long, triggerPrice: Long, tif: TimeInForce) {
        val buffer = bufferTl.get()
        val headerEncoder = headerEncoderTl.get()
        val newOrderEncoder = newOrderEncoderTl.get()
        while (!pub.isConnected) Thread.yield()
        headerEncoder.wrap(buffer, 0).blockLength(newOrderEncoder.sbeBlockLength()).templateId(newOrderEncoder.sbeTemplateId()).schemaId(newOrderEncoder.sbeSchemaId()).version(newOrderEncoder.sbeSchemaVersion())
        newOrderEncoder.wrap(buffer, headerEncoder.encodedLength()).userId(userId).symbolId(symbolId).price(price).qty(qty).side(side).seqId(seqId).orderType(type).triggerPrice(triggerPrice).tif(tif)
        offer(pub, buffer, headerEncoder.encodedLength() + newOrderEncoder.encodedLength())
    }
    private fun publishCancel(pub: ConcurrentPublication, userId: Long, orderId: Long, symbolId: Int, seqId: Long) {
        val buffer = bufferTl.get()
        val headerEncoder = headerEncoderTl.get()
        val cancelEncoder = cancelEncoderTl.get()
        while (!pub.isConnected) Thread.yield()
        headerEncoder.wrap(buffer, 0).blockLength(cancelEncoder.sbeBlockLength()).templateId(cancelEncoder.sbeTemplateId()).schemaId(cancelEncoder.sbeSchemaId()).version(cancelEncoder.sbeSchemaVersion())
        cancelEncoder.wrap(buffer, headerEncoder.encodedLength()).userId(userId).origOrderId(orderId).symbolId(symbolId).seqId(seqId)
        offer(pub, buffer, headerEncoder.encodedLength() + cancelEncoder.encodedLength())
    }
    fun sendBuffer(src: UnsafeBuffer, offset: Int, length: Int) {
        while (!publication.isConnected) Thread.yield()
        var result: Long
        val idle = BusySpinIdleStrategy()
        while (publication.offer(src, offset, length).also { result = it } < 0L) {
            if (result == io.aeron.Publication.CLOSED) throw IllegalStateException("Pub Closed")
            idle.idle()
        }
    }
    private fun offer(pub: ConcurrentPublication, buffer: UnsafeBuffer, length: Int) {
        var result: Long
        val idle = BusySpinIdleStrategy()
        while (pub.offer(buffer, 0, length).also { result = it } < 0L) {
            if (result == io.aeron.Publication.CLOSED) throw IllegalStateException("Pub Closed")
            idle.idle()
        }
    }
}

class AeronEventPublisher(private val aeron: Aeron) {
    private val publication = ExchangeConstants.retryAeronAction("EventPub") { aeron.addPublication(ExchangeConstants.PUB_CHANNEL, ExchangeConstants.EVENT_STREAM_ID) }
    private val buffer = UnsafeBuffer(ByteBuffer.allocateDirect(1024))
    private val headerEncoder = MessageHeaderEncoder()
    private val reportEncoder = ExecutionReportEncoder()
    private val snapshotEncoder = OrderBookSnapshotEncoder()

    fun sendExecutionReport(matchId: Long, makerId: Long, takerId: Long, makerUserId: Long, takerUserId: Long, price: Long, qty: Long, side: Side, execType: ExecType) {
        while (!publication.isConnected) Thread.yield()
        headerEncoder.wrap(buffer, 0).blockLength(reportEncoder.sbeBlockLength()).templateId(reportEncoder.sbeTemplateId()).schemaId(reportEncoder.sbeSchemaId()).version(reportEncoder.sbeSchemaVersion())
        reportEncoder.wrap(buffer, headerEncoder.encodedLength()).matchId(matchId).makerOrderId(makerId).takerOrderId(takerId).makerUserId(makerUserId).takerUserId(takerUserId).side(side).price(price).qty(qty).execType(execType)
        offer(headerEncoder.encodedLength() + reportEncoder.encodedLength())
    }

    fun sendOrderBookSnapshot(symbolId: Int, sequence: Long, bidPrices: LongArray, bidQtys: LongArray, askPrices: LongArray, askQtys: LongArray) {
        while (!publication.isConnected) Thread.yield()
        headerEncoder.wrap(buffer, 0).blockLength(snapshotEncoder.sbeBlockLength()).templateId(snapshotEncoder.sbeTemplateId()).schemaId(snapshotEncoder.sbeSchemaId()).version(snapshotEncoder.sbeSchemaVersion())
        snapshotEncoder.wrap(buffer, headerEncoder.encodedLength())
            .symbolId(symbolId)
            .seqId(sequence)
            .bidPrice0(bidPrices[0]).bidQty0(bidQtys[0])
            .bidPrice1(bidPrices[1]).bidQty1(bidQtys[1])
            .bidPrice2(bidPrices[2]).bidQty2(bidQtys[2])
            .bidPrice3(bidPrices[3]).bidQty3(bidQtys[3])
            .bidPrice4(bidPrices[4]).bidQty4(bidQtys[4])
            .askPrice0(askPrices[0]).askQty0(askQtys[0])
            .askPrice1(askPrices[1]).askQty1(askQtys[1])
            .askPrice2(askPrices[2]).askQty2(askQtys[2])
            .askPrice3(askPrices[3]).askQty3(askQtys[3])
            .askPrice4(askPrices[4]).askQty4(askQtys[4])
        offer(headerEncoder.encodedLength() + snapshotEncoder.encodedLength())
    }

    private fun offer(length: Int) {
        var result: Long
        val idle = BusySpinIdleStrategy()
        while (publication.offer(buffer, 0, length).also { result = it } < 0L) {
            if (result == io.aeron.Publication.CLOSED) throw IllegalStateException("Pub Closed")
            idle.idle()
        }
    }
}

class AeronSubscriber(private val aeron: Aeron, private val disruptorRingBuffer: RingBuffer<OrderEvent>, streamId: Int) {
    private val subscription = ExchangeConstants.retryAeronAction("Sub($streamId)") { aeron.addSubscription(ExchangeConstants.SUB_CHANNEL, streamId) }
    private val headerDecoder = MessageHeaderDecoder()
    private val newOrderDecoder = NewOrderSingleDecoder()
    private val cancelDecoder = OrderCancelDecoder()
    private val fragmentHandler = FragmentHandler { buffer, offset, length, header ->
        headerDecoder.wrap(buffer, offset)
        val bodyOffset = offset + headerDecoder.encodedLength()
        val seq = disruptorRingBuffer.next()
        val event = disruptorRingBuffer.get(seq)
        if (headerDecoder.templateId() == NewOrderSingleEncoder.TEMPLATE_ID) {
            newOrderDecoder.wrap(buffer, bodyOffset, headerDecoder.blockLength(), headerDecoder.version())
            event.userId = newOrderDecoder.userId()
            event.orderId = newOrderDecoder.seqId()
            event.symbolId = newOrderDecoder.symbolId()
            event.price = newOrderDecoder.price()
            event.qty = newOrderDecoder.qty()
            event.side = newOrderDecoder.side()
            event.orderType = newOrderDecoder.orderType()
            event.triggerPrice = newOrderDecoder.triggerPrice()
            event.tif = newOrderDecoder.tif()
            event.type = 1 // New
            event.seqId = newOrderDecoder.seqId()
        } else if (headerDecoder.templateId() == OrderCancelEncoder.TEMPLATE_ID) {
            cancelDecoder.wrap(buffer, bodyOffset, headerDecoder.blockLength(), headerDecoder.version())
            event.userId = cancelDecoder.userId()
            event.orderId = cancelDecoder.origOrderId()
            event.symbolId = cancelDecoder.symbolId()
            event.seqId = cancelDecoder.seqId()
            event.type = 2 // Cancel
        }
        disruptorRingBuffer.publish(seq)
    }
    fun poll(limit: Int): Int = subscription.poll(fragmentHandler, limit)
}

class AeronEventSubscriber(private val aeron: Aeron, private val onExecutionReport: (Long, Long, Long, Long, Side, Long, Long, ExecType) -> Unit) {
    private val subscription = ExchangeConstants.retryAeronAction("EventSub") { aeron.addSubscription(ExchangeConstants.SUB_CHANNEL, ExchangeConstants.EVENT_STREAM_ID) }
    private val headerDecoder = MessageHeaderDecoder()
    private val execReportDecoder = ExecutionReportDecoder()
    private val fragmentHandler = FragmentHandler { buffer, offset, length, header ->
        headerDecoder.wrap(buffer, offset)
        if (headerDecoder.templateId() == ExecutionReportEncoder.TEMPLATE_ID) {
            execReportDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version())
            onExecutionReport(execReportDecoder.makerOrderId(), execReportDecoder.takerOrderId(), execReportDecoder.makerUserId(), execReportDecoder.takerUserId(), execReportDecoder.side(), execReportDecoder.price(), execReportDecoder.qty(), execReportDecoder.execType())
        }
    }
    fun poll(limit: Int): Int = subscription.poll(fragmentHandler, limit)
}
