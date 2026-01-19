package com.exchange

import com.exchange.ipc.ExchangeConstants
import com.exchange.sbe.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.aeron.Aeron
import io.aeron.Subscription
import io.aeron.driver.MediaDriver
import io.aeron.logbuffer.FragmentHandler
import org.agrona.concurrent.BusySpinIdleStrategy
import org.agrona.concurrent.SigInt
import java.sql.Connection
import java.sql.SQLException

fun main(args: Array<String>) {
    println("Starting Persistence Worker...")
    
    // 1. Database Setup
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://localhost:5432/exchange"
        username = "postgres"
        password = "pass"
        maximumPoolSize = 5
        isAutoCommit = false
    }
    val dataSource = HikariDataSource(config)
    println("Connected to Database.")
    
    // Initialize Schema
    initSchema(dataSource)

    // 2. Setup Aeron
    val launchEmbeddedDriver = args.contains("--embedded-driver")
    val driver = if (launchEmbeddedDriver) {
        MediaDriver.launchEmbedded()
    } else {
        null
    }
    
    val aeronDir = driver?.aeronDirectoryName() ?: System.getProperty("aeron.dir") ?: io.aeron.CommonContext.getAeronDirectoryName()
    val ctx = Aeron.Context().aeronDirectoryName(aeronDir)
    val aeron = Aeron.connect(ctx)
    
    println("Persistence Worker connected to Aeron at $aeronDir")
    
    // 3. Subscribers (Listen to BOTH Command Stream and Event Stream)
    // Command Stream (10): New Order, Deposit, Withdraw
    val commandSubscription = aeron.addSubscription(ExchangeConstants.CHANNEL, ExchangeConstants.STREAM_ID)
    
    // Event Stream (20): Execution Reports
    val eventSubscription = aeron.addSubscription(ExchangeConstants.CHANNEL, ExchangeConstants.EVENT_STREAM_ID)
    
    // 4. Message Decoders
    val headerDecoder = MessageHeaderDecoder()
    val newOrderDecoder = NewOrderSingleDecoder()
    val depositDecoder = DepositDecoder()
    val execReportDecoder = ExecutionReportDecoder()

    // 5. Handler Logic
    val handler = FragmentHandler { buffer, offset, length, header ->
        headerDecoder.wrap(buffer, offset)
        
        val templateId = headerDecoder.templateId()
        val actingBlockLength = headerDecoder.blockLength()
        val actingVersion = headerDecoder.version()
        val bodyOffset = offset + headerDecoder.encodedLength()

        try {
            dataSource.connection.use { conn ->
                when (templateId) {
                    NewOrderSingleDecoder.TEMPLATE_ID -> {
                        newOrderDecoder.wrap(buffer, bodyOffset, actingBlockLength, actingVersion)
                        saveOrder(conn, newOrderDecoder)
                    }
                    DepositDecoder.TEMPLATE_ID -> {
                        depositDecoder.wrap(buffer, bodyOffset, actingBlockLength, actingVersion)
                        saveDeposit(conn, depositDecoder)
                    }
                    ExecutionReportDecoder.TEMPLATE_ID -> {
                        execReportDecoder.wrap(buffer, bodyOffset, actingBlockLength, actingVersion)
                        saveTrade(conn, execReportDecoder)
                    }
                }
                conn.commit()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    println("Listening for events...")
    
    SigInt.register {
        println("Shutting down worker...")
        aeron.close()
        driver?.close()
        dataSource.close()
    }
    
    val idleStrategy = BusySpinIdleStrategy()
    
    while (true) {
        val cmdFrags = commandSubscription.poll(handler, 10)
        val evtFrags = eventSubscription.poll(handler, 10)
        idleStrategy.idle(cmdFrags + evtFrags)
    }
}

fun initSchema(dataSource: HikariDataSource) {
    try {
        val schemaUrl = Thread.currentThread().contextClassLoader.getResource("db-schema.sql")
        if (schemaUrl == null) {
            println("WARN: db-schema.sql not found in classpath. Skipping schema initialization.")
            return
        }
        
        val sql = schemaUrl.readText()
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                val statements = sql.split(";").map { it.trim() }.filter { it.isNotEmpty() }
                for (s in statements) {
                    try {
                        stmt.execute(s)
                    } catch (e: Exception) {
                        // Ignore expected errors (e.g. table already exists)
                        // println("Error executing schema statement: $s")
                    }
                }
            }
            conn.commit() // Commit DDL changes
        }
        println("Database schema initialized.")
    } catch (e: Exception) {
        println("Failed to initialize schema: ${e.message}")
        e.printStackTrace()
    }
}

fun saveOrder(conn: Connection, decoder: NewOrderSingleDecoder) {
    val sql = "INSERT INTO orders (order_id, user_id, symbol_id, price, qty, side, status) VALUES (?, ?, ?, ?, ?, ?, 'NEW')"
    conn.prepareStatement(sql).use { stmt ->
        stmt.setLong(1, decoder.seqId())
        stmt.setLong(2, decoder.userId())
        stmt.setInt(3, decoder.symbolId())
        stmt.setLong(4, decoder.price())
        stmt.setLong(5, decoder.qty())
        stmt.setInt(6, decoder.side().value().toInt())
        stmt.executeUpdate()
    }
    
    val isBuy = decoder.side().value().toInt() == 1
    val currencyId = if (isBuy) 2 else 1
    val lockAmount = if (isBuy) decoder.price() * decoder.qty() else decoder.qty()
    
    val balanceSql = "UPDATE balances SET available = available - ?, locked = locked + ? WHERE user_id = ? AND currency_id = ?"
    conn.prepareStatement(balanceSql).use { stmt ->
        stmt.setLong(1, lockAmount)
        stmt.setLong(2, lockAmount)
        stmt.setLong(3, decoder.userId())
        stmt.setInt(4, currencyId)
        stmt.executeUpdate()
    }
}

fun saveDeposit(conn: Connection, decoder: DepositDecoder) {
    val sql = "INSERT INTO transfers (seq_id, user_id, currency_id, amount, type) VALUES (?, ?, ?, ?, 'DEPOSIT')"
    val updateBalanceSql = """
        INSERT INTO balances (user_id, currency_id, available) VALUES (?, ?, ?) 
        ON CONFLICT (user_id, currency_id) DO UPDATE SET available = balances.available + ?
    """
    
    conn.prepareStatement(sql).use { stmt ->
        stmt.setLong(1, decoder.seqId())
        stmt.setLong(2, decoder.userId())
        stmt.setInt(3, decoder.currencyId())
        stmt.setLong(4, decoder.amount())
        stmt.executeUpdate()
    }
    
    conn.prepareStatement(updateBalanceSql).use { stmt ->
        stmt.setLong(1, decoder.userId())
        stmt.setInt(2, decoder.currencyId())
        stmt.setLong(3, decoder.amount())
        stmt.setLong(4, decoder.amount())
        stmt.executeUpdate()
    }
}

fun saveTrade(conn: Connection, decoder: ExecutionReportDecoder) {
    val execType = decoder.execType()

    if (execType == ExecType.Trade) {
        val sql = "INSERT INTO trades (match_id, maker_order_id, taker_order_id, price, qty, side) VALUES (?, ?, ?, ?, ?, ?)"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, decoder.matchId())
            stmt.setLong(2, decoder.makerOrderId())
            stmt.setLong(3, decoder.takerOrderId())
            stmt.setLong(4, decoder.price())
            stmt.setLong(5, decoder.qty())
            stmt.setInt(6, decoder.side().value().toInt())
            stmt.executeUpdate()
        }

        val updateSql = "UPDATE orders SET status = 'FILLED', qty = qty - ? WHERE order_id IN (?, ?)"
        conn.prepareStatement(updateSql).use { stmt ->
            stmt.setLong(1, decoder.qty())
            stmt.setLong(2, decoder.makerOrderId())
            stmt.setLong(3, decoder.takerOrderId())
            stmt.executeUpdate()
        }
    } else if (execType == ExecType.Cancel) {
        val updateOrderSql = "UPDATE orders SET status = 'CANCELLED', qty = 0 WHERE order_id = ?"
        conn.prepareStatement(updateOrderSql).use { stmt ->
            stmt.setLong(1, decoder.makerOrderId())
            stmt.executeUpdate()
        }

        val isBuy = (decoder.side().value().toInt() == 1)
        val currencyId = if (isBuy) 2 else 1
        val unlockAmount = if (isBuy) decoder.price() * decoder.qty() else decoder.qty()
        
        val updateBalanceSql = "UPDATE balances SET available = available + ?, locked = locked - ? WHERE user_id = ? AND currency_id = ?"
        conn.prepareStatement(updateBalanceSql).use { stmt ->
            stmt.setLong(1, unlockAmount)
            stmt.setLong(2, unlockAmount)
            stmt.setLong(3, decoder.makerUserId())
            stmt.setInt(4, currencyId)
            stmt.executeUpdate()
        }
    }
}
