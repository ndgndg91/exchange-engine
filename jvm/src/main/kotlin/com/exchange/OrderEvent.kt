package com.exchange

import com.exchange.sbe.Side
import com.exchange.sbe.OrderType
import com.exchange.sbe.TimeInForce
import com.lmax.disruptor.EventFactory

// 1. Event: The carrier of data in the RingBuffer
class OrderEvent {
    var type: Int = 0 // 1=NewOrder, 2=Cancel
    var orderId: Long = 0
    var userId: Long = 0
    var symbolId: Int = 0
    var price: Long = 0
    var qty: Long = 0
    var side: Side = Side.NULL_VAL
    var orderType: OrderType = OrderType.NULL_VAL
    var triggerPrice: Long = 0
    var tif: TimeInForce = TimeInForce.GTC
    
    fun clear() {
        type = 0
        orderId = 0
        userId = 0
        symbolId = 0
        price = 0
        qty = 0
        side = Side.NULL_VAL
        orderType = OrderType.NULL_VAL
        triggerPrice = 0
        tif = TimeInForce.GTC
    }
}

// 2. Factory: Pre-allocates events
class OrderEventFactory : EventFactory<OrderEvent> {
    override fun newInstance(): OrderEvent {
        return OrderEvent()
    }
}
