package com.exchange.model

import com.exchange.sbe.Side
import com.exchange.sbe.OrderType

import com.exchange.sbe.TimeInForce

/**
 * Mutable Order object for Zero-GC.
 * Instead of creating new objects, we reuse instances from a pool.
 */
class Order {
    var orderId: Long = 0
    var userId: Long = 0
    var price: Long = 0
    var qty: Long = 0
    var side: Side = Side.NULL_VAL
    var type: OrderType = OrderType.NULL_VAL
    var triggerPrice: Long = 0
    var tif: TimeInForce = TimeInForce.GTC // Default GTC
    var timestamp: Long = 0

    // Reset state for reuse
    fun clear() {
        orderId = 0
        userId = 0
        price = 0
        qty = 0
        side = Side.NULL_VAL
        type = OrderType.NULL_VAL
        triggerPrice = 0
        tif = TimeInForce.GTC
        timestamp = 0
    }

    // Deep copy from another order (e.g. from an Event)
    fun set(id: Long, uId: Long, p: Long, q: Long, s: Side, t: OrderType, tp: Long = 0, tifVal: TimeInForce = TimeInForce.GTC) {
        this.orderId = id
        this.userId = uId
        this.price = p
        this.qty = q
        this.side = s
        this.type = t
        this.triggerPrice = tp
        this.tif = tifVal
    }
}
