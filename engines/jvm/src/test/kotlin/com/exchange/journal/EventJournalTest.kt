package com.exchange.journal

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.io.File

class EventJournalTest {

    @Test
    fun `should write and read messages`(@TempDir tempDir: File) {
        val journal = EventJournal(tempDir.absolutePath)
        
        val msg = "Hello World".toByteArray()
        // Use Heap ByteBuffer to avoid potential Direct Memory issues in test env for now
        val underlying = ByteBuffer.allocate(1024) 
        val buffer = UnsafeBuffer(underlying)
        
        if (buffer.byteBuffer() == null) {
            throw IllegalStateException("Underlying ByteBuffer is null")
        }
        
        buffer.putBytes(0, msg)
        
        // Write
        journal.write(buffer, 0, msg.size)
        
        // Read
        var readCount = 0
        val success = journal.read { byteBuffer -> 
            val readBytes = ByteArray(msg.size)
            // Note: The ByteBuffer position might be set by Chronicle. 
            // We need to check how Chronicle exposes it. 
            // Usually it points to start of data.
            byteBuffer.get(readBytes)
            assertArrayEquals(msg, readBytes)
            readCount++
        }
        
        assertTrue(success)
        assertEquals(1, readCount)
        
        journal.close()
    }
}
