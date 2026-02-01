package com.exchange

import com.exchange.ipc.AeronPublisher
import com.exchange.ipc.ExchangeConstants
import com.exchange.sbe.*
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
import com.exchange.sbe.OrderType
import com.exchange.sbe.Side
import com.exchange.sbe.TimeInForce

// Market Data Cache (In-Memory OrderBook Replica)
object OrderBookCache {
    data class L2Snapshot(
        val bidPrices: LongArray, val bidQtys: LongArray,
        val askPrices: LongArray, val askQtys: LongArray
    )

    private val snapshots = ConcurrentHashMap<Int, L2Snapshot>()

    fun update(symbolId: Int, snapshot: L2Snapshot) {
        snapshots[symbolId] = snapshot
    }

    fun get(symbolId: Int): L2Snapshot? {
        return snapshots[symbolId]
    }
}

/**
 * Netty TCP Gateway.
 */
class GatewayServer(private val publisher: AeronPublisher) {
    fun start(port: Int) {
        val bossGroup = NioEventLoopGroup(1)
        val workerGroup = NioEventLoopGroup()
        try {
            val b = ServerBootstrap()
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(SbeHandler(publisher))
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)

            println("Gateway TCP Server started on port $port")
            val f = b.bind(port).sync()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Netty HTTP Gateway.
 */
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class HttpApiHandler(private val publisher: AeronPublisher) : SimpleChannelInboundHandler<FullHttpRequest>() {
    private val seqIdGenerator = AtomicLong(System.currentTimeMillis())

    override fun channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest) {
        // Handle GET /orderbook
        if (req.method() == HttpMethod.GET && req.uri().startsWith("/orderbook")) {
             val uri = req.uri() // /orderbook?symbolId=1
             // Parse symbolId
             val symbolIdStr = if (uri.contains("symbolId=")) uri.substringAfter("symbolId=") else "1"
             val symbolId = try {
                 symbolIdStr.takeWhile { it.isDigit() }.toInt()
             } catch (e: Exception) { 1 }

             val snapshot = OrderBookCache.get(symbolId)
             if (snapshot == null) {
                 sendResponse(ctx, HttpResponseStatus.OK, "{ \"symbolId\": $symbolId, \"bids\": [], \"asks\": [] }") // Empty book
             } else {
                 // Format as JSON-like string manually to avoid JSON lib dependency
                 val sb = StringBuilder()
                 sb.append("{ \"symbolId\": ").append(symbolId).append(", \"bids\": [")
                 var firstBid = true
                 for (i in 0 until 5) {
                     if (snapshot.bidPrices[i] == 0L) continue
                     if (!firstBid) sb.append(", ")
                     sb.append("[").append(snapshot.bidPrices[i]).append(", ").append(snapshot.bidQtys[i]).append("]")
                     firstBid = false
                 }
                 sb.append("], \"asks\": [")
                 var firstAsk = true
                 for (i in 0 until 5) {
                     if (snapshot.askPrices[i] == 0L) continue
                     if (!firstAsk) sb.append(", ")
                     sb.append("[").append(snapshot.askPrices[i]).append(", ").append(snapshot.askQtys[i]).append("]")
                     firstAsk = false
                 }
                 sb.append("] }")
                 
                 sendResponse(ctx, HttpResponseStatus.OK, sb.toString())
             }
             return
        }

        if (req.method() != HttpMethod.POST) {
            sendResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "Only POST allowed (except /orderbook)")
            return
        }

        val content = req.content().toString(CharsetUtil.UTF_8)
        val uri = req.uri()

        try {
            val parts = content.split(",")
            val seqId = seqIdGenerator.incrementAndGet()

            if (uri.startsWith("/deposit")) {
                val userId = parts[0].trim().toLong()
                val currencyId = parts[1].trim().toInt()
                val amount = parts[2].trim().toLong()
                
                // Manual Encode for Deposit
                val buffer = UnsafeBuffer(ByteBuffer.allocateDirect(256))
                val header = MessageHeaderEncoder()
                val deposit = DepositEncoder()
                
                header.wrap(buffer, 0)
                    .blockLength(deposit.sbeBlockLength())
                    .templateId(deposit.sbeTemplateId())
                    .schemaId(deposit.sbeSchemaId())
                    .version(deposit.sbeSchemaVersion())
                
                deposit.wrap(buffer, header.encodedLength())
                    .userId(userId)
                    .currencyId(currencyId)
                    .amount(amount)
                    .seqId(seqId)
                    
                publisher.sendBuffer(buffer, 0, header.encodedLength() + deposit.encodedLength())
                sendResponse(ctx, HttpResponseStatus.OK, "Deposit Sent: $seqId")

            } else if (uri.startsWith("/order")) {
                val userId = parts[0].trim().toLong()
                val symbolId = parts[1].trim().toInt()
                val price = parts[2].trim().toLong()
                val qty = parts[3].trim().toLong()
                val sideVal = parts[4].trim().toInt()
                val side = if (sideVal == 1) Side.Buy else Side.Sell
                
                // Parse optional OrderType (1=Limit, 2=Market, 3=StopLimit, 4=StopMarket)
                val typeVal = if (parts.size > 5) parts[5].trim().toInt() else 1
                val type = when (typeVal) {
                    2 -> OrderType.Market
                    3 -> OrderType.StopLimit
                    4 -> OrderType.StopMarket
                    else -> OrderType.Limit
                }
                
                // Parse optional triggerPrice
                val triggerPrice = if (parts.size > 6) parts[6].trim().toLong() else 0L
                
                // Parse optional TimeInForce (0=GTC, 1=IOC, 2=FOK)
                val tifVal = if (parts.size > 7) parts[7].trim().toInt() else 0
                val tif = when (tifVal) {
                    1 -> TimeInForce.IOC
                    2 -> TimeInForce.FOK
                    else -> TimeInForce.GTC
                }
                
                publisher.sendOrder(userId, symbolId, price, qty, side, type, seqId, triggerPrice, tif)
                sendResponse(ctx, HttpResponseStatus.OK, "Order Sent: $seqId")
            } else if (uri.startsWith("/cancel")) {
                val userId = parts[0].trim().toLong()
                val orderId = parts[1].trim().toLong()
                val symbolId = parts[2].trim().toInt()
                
                publisher.sendCancel(userId, orderId, symbolId, seqId)
                sendResponse(ctx, HttpResponseStatus.OK, "Cancel Sent: $seqId")
            } else {
                sendResponse(ctx, HttpResponseStatus.NOT_FOUND, "Unknown API")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, "Error: ${e.message}")
        }
    }

    private fun sendResponse(ctx: ChannelHandlerContext, status: HttpResponseStatus, msg: String) {
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(msg, CharsetUtil.UTF_8))
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json") // Changed to JSON
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
            val nioBuffer = directBuffer.byteBuffer()
            nioBuffer.clear()
            if (length > nioBuffer.capacity()) {
                println("Message too large: $length")
                return
            }
            nioBuffer.limit(length)
            byteBuf.readBytes(nioBuffer)
            publisher.sendBuffer(directBuffer, 0, length)
        } finally {
            byteBuf.release()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        cause.printStackTrace()
        ctx.close()
    }
}

class MarketDataSubscriber(aeron: Aeron) {
    private val subscription = aeron.addSubscription(ExchangeConstants.CHANNEL, ExchangeConstants.EVENT_STREAM_ID)
    private val headerDecoder = MessageHeaderDecoder()
    private val snapshotDecoder = OrderBookSnapshotDecoder()

    private val handler = FragmentHandler { buffer, offset, length, header ->
        headerDecoder.wrap(buffer, offset)
        val templateId = headerDecoder.templateId()
        val actingBlockLength = headerDecoder.blockLength()
        val actingVersion = headerDecoder.version()
        val bodyOffset = offset + headerDecoder.encodedLength()

        if (templateId == OrderBookSnapshotDecoder.TEMPLATE_ID) {
            snapshotDecoder.wrap(buffer, bodyOffset, actingBlockLength, actingVersion)
            
            val bidPrices = LongArray(5)
            val bidQtys = LongArray(5)
            val askPrices = LongArray(5)
            val askQtys = LongArray(5)
            
            bidPrices[0] = snapshotDecoder.bidPrice0(); bidQtys[0] = snapshotDecoder.bidQty0()
            bidPrices[1] = snapshotDecoder.bidPrice1(); bidQtys[1] = snapshotDecoder.bidQty1()
            bidPrices[2] = snapshotDecoder.bidPrice2(); bidQtys[2] = snapshotDecoder.bidQty2()
            bidPrices[3] = snapshotDecoder.bidPrice3(); bidQtys[3] = snapshotDecoder.bidQty3()
            bidPrices[4] = snapshotDecoder.bidPrice4(); bidQtys[4] = snapshotDecoder.bidQty4()

            askPrices[0] = snapshotDecoder.askPrice0(); askQtys[0] = snapshotDecoder.askQty0()
            askPrices[1] = snapshotDecoder.askPrice1(); askQtys[1] = snapshotDecoder.askQty1()
            askPrices[2] = snapshotDecoder.askPrice2(); askQtys[2] = snapshotDecoder.askQty2()
            askPrices[3] = snapshotDecoder.askPrice3(); askQtys[3] = snapshotDecoder.askQty3()
            askPrices[4] = snapshotDecoder.askPrice4(); askQtys[4] = snapshotDecoder.askQty4()

            // Update Cache
            OrderBookCache.update(snapshotDecoder.symbolId(), OrderBookCache.L2Snapshot(bidPrices, bidQtys, askPrices, askQtys))
        }
    }

    fun poll(limit: Int): Int {
        return subscription.poll(handler, limit)
    }
}

fun main(args: Array<String>) {
    println("Starting Production Gateway Server...")

    val launchEmbeddedDriver = args.contains("--embedded-driver")
    val driver = if (launchEmbeddedDriver) MediaDriver.launchEmbedded() else null
    val aeronDir = driver?.aeronDirectoryName() ?: System.getProperty("aeron.dir") ?: io.aeron.CommonContext.getAeronDirectoryName()
    
    val ctx = Aeron.Context().aeronDirectoryName(aeronDir)
    val aeron = Aeron.connect(ctx)
    val publisher = AeronPublisher(aeron)

    // Start Market Data Subscriber (Background Thread)
    val mdSubscriber = MarketDataSubscriber(aeron)
    Thread {
        println("Gateway Market Data Listener Started.")
        val idle = org.agrona.concurrent.BusySpinIdleStrategy()
        while (true) {
            val frags = mdSubscriber.poll(10)
            idle.idle(frags)
        }
    }.start()

    val tcpServer = GatewayServer(publisher)
    tcpServer.start(8082)
    
    val httpServer = HttpGatewayServer(publisher)
    httpServer.start(8080)
}