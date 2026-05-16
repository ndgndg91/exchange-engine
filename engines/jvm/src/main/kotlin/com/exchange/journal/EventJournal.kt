package com.exchange.journal

import net.openhft.chronicle.queue.ChronicleQueue
import net.openhft.chronicle.queue.ExcerptAppender
import net.openhft.chronicle.queue.ExcerptTailer
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder
import org.agrona.DirectBuffer
import org.agrona.MutableDirectBuffer
import java.io.File
import java.nio.ByteBuffer

import net.openhft.chronicle.bytes.BytesStore

class EventJournal(private val path: String) {

    private val queue: ChronicleQueue = SingleChronicleQueueBuilder.binary(path).build()
    private val appender: ExcerptAppender = queue.acquireAppender()
    private val tailer: ExcerptTailer = queue.createTailer()

    /**
     * Write a binary message to the journal.
     * Uses Agrona Buffer for Zero-Copy compatibility with SBE.
     */
    fun write(buffer: DirectBuffer, offset: Int, length: Int) {
        appender.writeDocument { wire -> 
            val bs = BytesStore.wrap(buffer.byteBuffer())
            wire.bytes().write(bs, offset.toLong(), length.toLong())
        }
    }

    /**
     * Read next message from journal.
     * Returns true if a message was read, false otherwise.
     */
    fun read(handler: (ByteBuffer) -> Unit): Boolean {
        return tailer.readDocument { wire ->
            val bytes = wire.bytes()
            val length = bytes.readRemaining().toInt()
            if (length > 0) {
                // Safe copy to avoid underlyingObject issues
                val tempBb = ByteBuffer.allocate(length)
                bytes.read(tempBb)
                tempBb.flip()
                handler(tempBb)
            }
        }
    }
    
    fun close() {
        queue.close()
    }
}
