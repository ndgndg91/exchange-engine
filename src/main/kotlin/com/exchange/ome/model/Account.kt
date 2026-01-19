package com.exchange.ome.model

import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap

class Account(val userId: Long) {
    // Currency ID -> Balance
    val balances = IntObjectHashMap<Balance>()

    fun getBalance(currencyId: Int): Balance {
        var balance = balances.get(currencyId)
        if (balance == null) {
            balance = Balance()
            balances.put(currencyId, balance)
        }
        return balance
    }
}
