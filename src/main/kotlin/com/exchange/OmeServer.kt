package com.exchange

import com.exchange.ipc.AeronPublisher
import com.exchange.journal.EventJournal
import com.exchange.ome.OmeEngine
import com.exchange.sbe.Side
import com.exchange.sbe.OrderType
import io.aeron.Aeron
import io.aeron.driver.MediaDriver
import org.agrona.concurrent.BusySpinIdleStrategy
import org.agrona.concurrent.SigInt

fun main(args: Array<String>) {
    println("Starting OME Server...")

    // 1. Infrastructure Setup
    val launchEmbeddedDriver = args.contains("--embedded-driver")
    val driver = if (launchEmbeddedDriver) {
        println("Launching Embedded Media Driver...")
        MediaDriver.launchEmbedded()
    } else {
        null
    }

    val aeronDir = driver?.aeronDirectoryName() ?: System.getProperty("aeron.dir") ?: io.aeron.CommonContext.getAeronDirectoryName()
    val ctx = Aeron.Context().aeronDirectoryName(aeronDir)
    val aeron = Aeron.connect(ctx)
    
    val publisher = AeronPublisher(aeron)
    val journal = EventJournal("ome-journal")
    
    // 2. Initialize Engine
    val omeEngine = OmeEngine(publisher, journal, aeron)

    println("OME Server Ready.")
    
    // Initial Funding for Simulation Users (Publishing via Aeron so Worker can save)
    if (args.contains("--simulate")) {
        println("Funding simulation users...")
        omeEngine.injectDeposit(1001, 2, 1_000_000_000_000_000L, 1) // User 1001: 1 Quadrillion USDT
        Thread.sleep(100)
        omeEngine.injectDeposit(1002, 1, 1_000_000_000_000_000L, 2) // User 1002: 1 Quadrillion BTC
        Thread.sleep(100)
    }

    SigInt.register { 
        println("Shutting down OME...")
        journal.close()
        aeron.close()
        driver?.close()
    }
    
    // 3. Simulation / Input Loop
    val runSimulation = args.contains("--simulate")
    val idleStrategy = BusySpinIdleStrategy()
    
    if (runSimulation) {
        println("Running in SIMULATION mode...")
        var seqId = 1000L // Start after initial funding seqIds
        while (true) {
            // Feedback Loop (Settlement)
            val fragments = omeEngine.pollEvents()
            
            // Generate Load
            seqId++
            // Slow down for demo
            if (seqId % 1000 == 0L) {
                Thread.sleep(1)
            } else {
                idleStrategy.idle(fragments)
            }
            
            val side = if (seqId % 2 == 0L) Side.Buy else Side.Sell
            val userId = if (side == Side.Buy) 1001L else 1002L
            
            // Force Match: Buy High, Sell Low
            val price = if (side == Side.Buy) {
                 50001000L + (Math.random() * 100).toLong()
            } else {
                 50000000L + (Math.random() * 100).toLong()
            }
            
            omeEngine.onOrderRequest(
                userId = userId,
                symbolId = 1,
                price = price,
                qty = 1L, 
                side = side, 
                type = OrderType.Limit, 
                seqId = seqId
            )

            if (seqId % 10000 == 0L) {
                println("OME: Processed $seqId orders...")
            }
        }
    } else {
        println("Running in SERVER mode (Waiting for events)...")
        // Gateway -> OME (Commands) -> ME
        // ME -> OME (Events)
        while (true) {
            val cmdFrags = omeEngine.pollCommands()
            val evtFrags = omeEngine.pollEvents()
            idleStrategy.idle(cmdFrags + evtFrags)
        }
    }
}

