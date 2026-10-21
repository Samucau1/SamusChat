package com.samuschat

import com.samuschat.ui.call.ScreenAudioWire
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ScreenAudioWireTest {
    @Test fun opusPacketsAreBoundedVersionedAndLittleEndian() {
        val packet = ScreenAudioWire.packet(byteArrayOf(1, 2, 3), 77)
        assertTrue(ScreenAudioWire.isOpus(packet))
        assertEquals(77, ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getInt(4))
        for (size in listOf(0, 8, 4097)) assertFalse(ScreenAudioWire.isOpus(ByteArray(size)))
    }
    @Test fun compatibilityDownsamplePreservesNegativeSamplesWithoutOverflow() {
        val input = ByteBuffer.allocate(1920).order(ByteOrder.LITTLE_ENDIAN)
        repeat(960) { input.putShort(-12000) }
        val output = ScreenAudioWire.legacy(input.array())
        assertEquals(640, output.size)
        assertEquals((-12000).toShort(), ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN).short)
    }
}
