package com.exchange.ome

import com.exchange.ipc.AeronPublisher
import com.exchange.ipc.AeronEventSubscriber
import com.exchange.journal.EventJournal
import com.exchange.sbe.*
import org.agrona.concurrent.UnsafeBuffer
import java.nio.ByteBuffer
import io.aeron.Aeron

class OmeEngine(
    private val publisher: AeronPublisher,
    private val journal: EventJournal,
    aeron: Aeron
) {
    private val riskEngine = RiskEngine()
    private val eventSubscriber: AeronEventSubscriber
    
    // Command Subscriber (Gateway -> OME)
    private val commandSubscription = aeron.addSubscription(com.exchange.ipc.ExchangeConstants.CHANNEL, com.exchange.ipc.ExchangeConstants.STREAM_ID)
    private val cmdHeaderDecoder = MessageHeaderDecoder()
    private val cmdNewOrderDecoder = NewOrderSingleDecoder()
    private val cmdDepositDecoder = DepositDecoder()
    private val cmdCancelDecoder = OrderCancelDecoder()
    
    // Reusing buffers for encoding before sending/journaling
    private val tempBuffer = UnsafeBuffer(ByteBuffer.allocateDirect(1024))
    private val headerEncoder = MessageHeaderEncoder()
    private val newOrderEncoder = NewOrderSingleEncoder()
    private val depositEncoder = DepositEncoder()
    private val withdrawRequestEncoder = WithdrawRequestEncoder()
    private val cancelEncoder = OrderCancelEncoder()

    init {
        // Initialize Event Subscriber for Feedback Loop
        eventSubscriber = AeronEventSubscriber(aeron) { makerOrderId, takerOrderId, makerUserId, takerUserId, side, price, qty, execType ->
            if (execType == ExecType.Trade) {
                 // makerUserId and takerUserId are now available
                 riskEngine.onTrade(makerUserId, takerUserId, side, price, qty)
            } else if (execType == ExecType.Cancel) {
                 // Refund Locked Assets
                 // For Cancel: makerOrderId is the Cancelled Order ID. makerUserId is the owner.
                 riskEngine.onCancel(makerOrderId, side, price, qty, makerUserId)
            }
        }
    }
    
    private val commandHandler = io.aeron.logbuffer.FragmentHandler { buffer, offset, length, header ->
        cmdHeaderDecoder.wrap(buffer, offset)
        val templateId = cmdHeaderDecoder.templateId()
        val actingBlockLength = cmdHeaderDecoder.blockLength()
        val actingVersion = cmdHeaderDecoder.version()
        val bodyOffset = offset + cmdHeaderDecoder.encodedLength()

        if (templateId == NewOrderSingleEncoder.TEMPLATE_ID) {
            cmdNewOrderDecoder.wrap(buffer, bodyOffset, actingBlockLength, actingVersion)
            onOrderRequest(
                cmdNewOrderDecoder.userId(),
                cmdNewOrderDecoder.symbolId(),
                cmdNewOrderDecoder.price(),
                cmdNewOrderDecoder.qty(),
                cmdNewOrderDecoder.side(),
                cmdNewOrderDecoder.orderType(),
                cmdNewOrderDecoder.seqId()
            )
        } else if (templateId == DepositDecoder.TEMPLATE_ID) {
            cmdDepositDecoder.wrap(buffer, bodyOffset, actingBlockLength, actingVersion)
            onDeposit(
                cmdDepositDecoder.userId(),
                cmdDepositDecoder.currencyId(),
                cmdDepositDecoder.amount(),
                cmdDepositDecoder.seqId()
            )
        } else if (templateId == OrderCancelEncoder.TEMPLATE_ID) {
            cmdCancelDecoder.wrap(buffer, bodyOffset, actingBlockLength, actingVersion)
            onCancelRequest(
                cmdCancelDecoder.userId(),
                cmdCancelDecoder.origOrderId(),
                cmdCancelDecoder.symbolId(),
                cmdCancelDecoder.seqId()
            )
        }
    }
    
    fun pollCommands(): Int {
        return commandSubscription.poll(commandHandler, 10)
    }
    
    fun pollEvents(): Int {
        return eventSubscriber.poll(10)
    }

    fun onCancelRequest(userId: Long, orderId: Long, symbolId: Int, seqId: Long) {
        // 1. Encode
        headerEncoder.wrap(tempBuffer, 0)
            .blockLength(cancelEncoder.sbeBlockLength())
            .templateId(cancelEncoder.sbeTemplateId())
            .schemaId(cancelEncoder.sbeSchemaId())
            .version(cancelEncoder.sbeSchemaVersion())
            
        cancelEncoder.wrap(tempBuffer, headerEncoder.encodedLength())
            .userId(userId)
            .origOrderId(orderId)
            .symbolId(symbolId)
            .seqId(seqId)
            
        val length = headerEncoder.encodedLength() + cancelEncoder.encodedLength()
        
        // 2. Journal
        journal.write(tempBuffer, 0, length)
        
        // 3. Publish to ME (Stream 11)
        publisher.sendCancelToEngine(userId, orderId, symbolId, seqId)
    }

    fun onOrderRequest(
        userId: Long, 
        symbolId: Int, 
        price: Long, 
        qty: Long, 
        side: Side, 
        type: OrderType, 
        seqId: Long
    ) {
        // 1. Risk Check
        if (!riskEngine.preCheckOrder(userId, symbolId, side, price, qty)) {
            println("Risk Check Failed for User $userId Side $side")
            return
        }

        // 2. Encode
        headerEncoder.wrap(tempBuffer, 0)
            .blockLength(newOrderEncoder.sbeBlockLength())
            .templateId(newOrderEncoder.sbeTemplateId())
            .schemaId(newOrderEncoder.sbeSchemaId())
            .version(newOrderEncoder.sbeSchemaVersion())

        newOrderEncoder.wrap(tempBuffer, headerEncoder.encodedLength())
            .userId(userId)
            .symbolId(symbolId)
            .price(price)
            .qty(qty)
            .side(side)
            .seqId(seqId)
            .orderType(type)

        val length = headerEncoder.encodedLength() + newOrderEncoder.encodedLength()

        // 3. Journal
        journal.write(tempBuffer, 0, length)

        // 4. Publish to ME (Stream 11)
        publisher.sendOrderToEngine(userId, symbolId, price, qty, side, type, seqId)
    }

    fun onDeposit(userId: Long, currencyId: Int, amount: Long, seqId: Long) {
        // 1. Update Balance
        riskEngine.onDeposit(userId, currencyId, amount)

        // 2. Encode
        headerEncoder.wrap(tempBuffer, 0)
            .blockLength(depositEncoder.sbeBlockLength())
            .templateId(depositEncoder.sbeTemplateId())
            .schemaId(depositEncoder.sbeSchemaId())
            .version(depositEncoder.sbeSchemaVersion())
            
        depositEncoder.wrap(tempBuffer, headerEncoder.encodedLength())
            .userId(userId)
            .currencyId(currencyId)
            .amount(amount)
            .seqId(seqId)
            
        val length = headerEncoder.encodedLength() + depositEncoder.encodedLength()
        
        // 3. Journal
        journal.write(tempBuffer, 0, length)
        
        // 4. Publish - REMOVED to avoid duplication loop (Worker already listens to Stream 10)
    }
    
    // For Simulation / Injection (Publishes to Aeron so Worker can see it)
    fun injectDeposit(userId: Long, currencyId: Int, amount: Long, seqId: Long) {
        onDeposit(userId, currencyId, amount, seqId)
        
        // Re-encode because tempBuffer might be reused (though in single thread it's safe, being explicit is better)
        // Actually onDeposit leaves data in tempBuffer.
        val length = headerEncoder.encodedLength() + depositEncoder.encodedLength()
        publisher.sendBuffer(tempBuffer, 0, length)
    }
    
    fun onWithdrawRequest(userId: Long, currencyId: Int, amount: Long, seqId: Long) {
        // 1. Risk Check (Lock funds)
        if (!riskEngine.onWithdrawRequest(userId, currencyId, amount)) {
            println("Withdrawal Failed: Insufficient Funds for User $userId")
            return
        }
        
        // 2. Encode
        headerEncoder.wrap(tempBuffer, 0)
            .blockLength(withdrawRequestEncoder.sbeBlockLength())
            .templateId(withdrawRequestEncoder.sbeTemplateId())
            .schemaId(withdrawRequestEncoder.sbeSchemaId())
            .version(withdrawRequestEncoder.sbeSchemaVersion())
            
        withdrawRequestEncoder.wrap(tempBuffer, headerEncoder.encodedLength())
            .userId(userId)
            .currencyId(currencyId)
            .amount(amount)
            .seqId(seqId)
            
        val length = headerEncoder.encodedLength() + withdrawRequestEncoder.encodedLength()
        
        // 3. Journal
        journal.write(tempBuffer, 0, length)
        
        // 4. Publish
        publisher.sendBuffer(tempBuffer, 0, length)
    }
}