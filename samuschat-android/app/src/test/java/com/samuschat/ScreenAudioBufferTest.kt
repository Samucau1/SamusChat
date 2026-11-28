package com.samuschat

import com.samuschat.ui.call.ScreenAudioBuffer
import org.junit.Assert.*
import org.junit.Test

class ScreenAudioBufferTest {
    private val buffer = ScreenAudioBuffer()
    private fun offer(id: Int, now: Long) = buffer.offer(id.toLong(), byteArrayOf(id.toByte()), now)
    private fun take(now: Long) = buffer.poll(now)?.first()?.toInt()

    @Test fun restoresOrderAndRejectsDuplicatePackets() {
        offer(2, 0); offer(1, 10)
        assertNull(take(59)); assertEquals(1, take(60)); assertEquals(2, take(60))
        offer(4, 65); assertNull(take(70))
        offer(3, 75); offer(4, 76)
        assertEquals(3, take(76)); assertEquals(4, take(76))
        offer(3, 80); assertNull(take(90))
    }

    @Test fun missingPacketDoesNotHoldUpPlayback() {
        offer(10, 0); assertEquals(10, take(60))
        offer(12, 65); assertNull(take(104)); assertEquals(12, take(105))
        offer(13, 110); assertEquals(13, take(110))
    }

    @Test fun limitsBacklogAfterABurst() {
        for (id in 1..20) offer(id, 0)
        assertEquals((11..20).toList(), (1..10).map { take(60) })
        assertTrue(buffer.isEmpty())
    }
}
