package com.exchange

import com.exchange.ipc.AeronPublisher
import com.exchange.ipc.ExchangeConstants
import com.exchange.sbe.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.aeron.Aeron
import io.aeron.driver.MediaDriver
import io.aeron.logbuffer.FragmentHandler
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.util.CharsetUtil
import org.agrona.concurrent.UnsafeBuffer
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

// Market Data Cache
object OrderBookCache {
    data class L2Snapshot(
        val bidPrices: LongArray, val bidQtys: LongArray,
        val askPrices: LongArray, val askQtys: LongArray
    )
    private val snapshots = ConcurrentHashMap<Int, L2Snapshot>()
    fun update(symbolId: Int, snapshot: L2Snapshot) { snapshots[symbolId] = snapshot }
    fun get(symbolId: Int): L2Snapshot? = snapshots[symbolId]
}

class HttpGatewayServer(private val publisher: AeronPublisher) {
    fun start(port: Int) {
        val bossGroup = NioEventLoopGroup(1)
        val workerGroup = NioEventLoopGroup()
        try {
            val b = ServerBootstrap()
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(HttpServerCodec())
                        ch.pipeline().addLast(HttpObjectAggregator(65536))
                        ch.pipeline().addLast(HttpApiHandler(publisher))
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
            println("Gateway HTTP Server started on port $port")
            b.bind(port).sync()
        } catch (e: Exception) { e.printStackTrace() }
    }
}

class HttpApiHandler(private val publisher: AeronPublisher) : SimpleChannelInboundHandler<FullHttpRequest>() {
    private val seqIdGenerator = AtomicLong(System.currentTimeMillis())
    private val mapper = jacksonObjectMapper()

    data class OrderReq(val user_id: Long, val symbol_id: Int, val price: Long, val qty: Long, val side: Int)
    data class DepositReq(val user_id: Long, val currency_id: Int, val amount: Long)

    override fun channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest) {
        if (req.method() == HttpMethod.GET && req.uri().startsWith("/orderbook")) {
             val symbolId = req.uri().substringAfter("symbolId=", "1").takeWhile { it.isDigit() }.toInt()
             val snapshot = OrderBookCache.get(symbolId)
             val res = if (snapshot == null) "{ \"symbolId\": $symbolId, \"bids\": [], \"asks\": [] }" else {
                 val sb = StringBuilder("{ \"symbolId\": $symbolId, \"bids\": [")
                 for (i in 0 until 5) if (snapshot.bidPrices[i] != 0L) sb.append("[${snapshot.bidPrices[i]}, ${snapshot.bidQtys[i]}],")
                 if (sb.endsWith(",")) sb.setLength(sb.length - 1)
                 sb.append("], \"asks\": [")
                 for (i in 0 until 5) if (snapshot.askPrices[i] != 0L) sb.append("[${snapshot.askPrices[i]}, ${snapshot.askQtys[i]}],")
                 if (sb.endsWith(",")) sb.setLength(sb.length - 1)
                 sb.append("] }")
                 sb.toString()
             }
             sendResponse(ctx, HttpResponseStatus.OK, res)
             return
        }

        if (req.method() != HttpMethod.POST) {
            sendResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "POST Only")
            return
        }

        val content = req.content().toString(CharsetUtil.UTF_8)
        val seqId = seqIdGenerator.incrementAndGet()

        try {
            if (req.uri().startsWith("/deposit")) {
                val d = mapper.readValue<DepositReq>(content)
                val buffer = UnsafeBuffer(ByteBuffer.allocateDirect(256))
                val deposit = DepositEncoder()
                val header = MessageHeaderEncoder()
                header.wrap(buffer, 0).blockLength(deposit.sbeBlockLength()).templateId(deposit.sbeTemplateId()).schemaId(deposit.sbeSchemaId()).version(deposit.sbeSchemaVersion())
                deposit.wrap(buffer, header.encodedLength()).userId(d.user_id).currencyId(d.currency_id).amount(d.amount).seqId(seqId)
                publisher.sendBuffer(buffer, 0, header.encodedLength() + deposit.encodedLength())
                sendResponse(ctx, HttpResponseStatus.OK, "Deposit Sent: $seqId")
            } else if (req.uri().startsWith("/order")) {
                val o = mapper.readValue<OrderReq>(content)
                val side = if (o.side == 1) Side.Buy else Side.Sell
                publisher.sendOrder(o.user_id, o.symbol_id, o.price, o.qty, side, OrderType.Limit, seqId)
                sendResponse(ctx, HttpResponseStatus.OK, "Order Sent: $seqId")
            }
        } catch (e: Exception) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, "Error: ${e.message}")
        }
    }

    private fun sendResponse(ctx: ChannelHandlerContext, status: HttpResponseStatus, msg: String) {
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(msg, CharsetUtil.UTF_8))
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json")
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())
        ctx.writeAndFlush(response)
    }
}

class SbeHandler(private val publisher: AeronPublisher) : ChannelInboundHandlerAdapter() {
    private val directBuffer = UnsafeBuffer(ByteBuffer.allocateDirect(1024))
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val byteBuf = msg as ByteBuf
        try {
            val length = byteBuf.readableBytes()
            val nioBuffer = directBuffer.byteBuffer().clear().limit(length) as ByteBuffer
            byteBuf.readBytes(nioBuffer)
            publisher.sendBuffer(directBuffer, 0, length)
        } finally { byteBuf.release() }
    }
}

class MarketDataSubscriber(aeron: Aeron) {
    private val subscription = aeron.addSubscription(ExchangeConstants.CHANNEL, ExchangeConstants.EVENT_STREAM_ID)
    private val headerDecoder = MessageHeaderDecoder()
    private val snapshotDecoder = OrderBookSnapshotDecoder()
    private val handler = FragmentHandler { buffer, offset, _, _ ->
        headerDecoder.wrap(buffer, offset)
        if (headerDecoder.templateId() == OrderBookSnapshotDecoder.TEMPLATE_ID) {
            snapshotDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version())
            val bp = LongArray(5); val bq = LongArray(5); val ap = LongArray(5); val aq = LongArray(5)
            bp[0]=snapshotDecoder.bidPrice0(); bq[0]=snapshotDecoder.bidQty0()
            bp[1]=snapshotDecoder.bidPrice1(); bq[1]=snapshotDecoder.bidQty1()
            bp[2]=snapshotDecoder.bidPrice2(); bq[2]=snapshotDecoder.bidQty2()
            bp[3]=snapshotDecoder.bidPrice3(); bq[3]=snapshotDecoder.bidQty3()
            bp[4]=snapshotDecoder.bidPrice4(); bq[4]=snapshotDecoder.bidQty4()
            ap[0]=snapshotDecoder.askPrice0(); aq[0]=snapshotDecoder.askQty0()
            ap[1]=snapshotDecoder.askPrice1(); aq[1]=snapshotDecoder.askQty1()
            ap[2]=snapshotDecoder.askPrice2(); aq[2]=snapshotDecoder.askQty2()
            ap[3]=snapshotDecoder.askPrice3(); aq[3]=snapshotDecoder.askQty3()
            ap[4]=snapshotDecoder.askPrice4(); aq[4]=snapshotDecoder.askQty4()
            OrderBookCache.update(snapshotDecoder.symbolId(), OrderBookCache.L2Snapshot(bp, bq, ap, aq))
        }
    }
    fun poll(limit: Int): Int = subscription.poll(handler, limit)
}

fun main(args: Array<String>) {
    println("Starting Production Gateway Server...")
    val launchEmbeddedDriver = args.contains("--embedded-driver")
    val driver = if (launchEmbeddedDriver) MediaDriver.launchEmbedded() else null
    val aeronDir = driver?.aeronDirectoryName() ?: System.getProperty("aeron.dir") ?: io.aeron.CommonContext.getAeronDirectoryName()
    val ctx = Aeron.Context().aeronDirectoryName(aeronDir)
    val aeron = Aeron.connect(ctx)
    val publisher = AeronPublisher(aeron)
    val mdSubscriber = MarketDataSubscriber(aeron)
    Thread {
        val idle = org.agrona.concurrent.BusySpinIdleStrategy()
        while (true) idle.idle(mdSubscriber.poll(10))
    }.start()
    HttpGatewayServer(publisher).start(8080)
}
