package com.exchange.ome.model

class Balance {
    var available: Long = 0
    var locked: Long = 0

    fun total(): Long {
        return available + locked
    }
}
