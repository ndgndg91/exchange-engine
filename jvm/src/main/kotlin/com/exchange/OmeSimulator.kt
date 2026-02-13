package com.exchange

import com.exchange.ipc.AeronPublisher
import com.exchange.journal.EventJournal
import com.exchange.ome.OmeEngine
import com.exchange.sbe.OrderType
import com.exchange.sbe.Side
import io.aeron.Aeron
import io.aeron.driver.MediaDriver
import org.agrona.concurrent.SigInt

fun main() {
    println("Starting OME Simulator (Gateway + Risk + Sequencer)...")

    // 1. Start Embedded Media Driver (Aeron Router)
    val driver = MediaDriver.launchEmbedded()
    val ctx = Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName())
    val aeron = Aeron.connect(ctx)
    
    val publisher = AeronPublisher(aeron)
    
    // 2. Initialize Journal
    val journal = EventJournal("ome-journal")
    
    // 3. Initialize OME Engine
    val omeEngine = OmeEngine(publisher, journal, aeron) // Pass Aeron to OmeEngine

    println("OME Engine Ready. Press Ctrl+C to stop.")
    SigInt.register { 
        journal.close()
        aeron.close()
        driver.close()
    }

    var seqId = 0L
    val idleStrategy = org.agrona.concurrent.BusySpinIdleStrategy()
    
    // 4. Publish Loop
    while (true) {
        // Poll for Execution Reports (Feedback Loop)
        val fragments = omeEngine.pollEvents()
        idleStrategy.idle(fragments)
        
        seqId++
        val side = if (seqId % 2 == 0L) Side.Buy else Side.Sell
        
        // Use User 1001 (USDT Holder) for Buys, 1002 (BTC Holder) for Sells
        val userId = if (side == Side.Buy) 1001L else 1002L
        
        val price = 50000000L + (Math.random() * 1000).toLong()
        
        omeEngine.onOrderRequest(
            userId = userId,
            symbolId = 1,
            price = price,
            qty = 10000L, // 0.0001 BTC
            side = side,
            type = OrderType.Limit,
            seqId = seqId
        )
        
        if (seqId % 10000 == 0L) {
            println("Processed $seqId orders...")
        }
        
        // Control throughput
        Thread.sleep(1) 
    }
}
