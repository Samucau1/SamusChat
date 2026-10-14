package com.samuschat

import com.samuschat.ui.call.captureDimensions
import org.junit.Assert.*
import org.junit.Test

class ScreenCaptureDimensionsTest {
    @Test fun motoDisplayRotatesWithoutEmbeddingLandscapeInPortrait() {
        val portrait = captureDimensions(720, 1512)
        val landscape = captureDimensions(1512, 720)
        assertEquals(960, portrait.height)
        assertEquals(portrait.height, landscape.width)
        assertEquals(portrait.width, landscape.height)
        assertTrue(landscape.width > landscape.height)
        assertEquals(portrait, captureDimensions(720, 1512))
    }
    @Test fun preservesAspectRatioAndEvenEncoderDimensions() {
        for ((width, height) in listOf(1920 to 1080, 1080 to 1920, 1080 to 2400, 2560 to 1080, 640 to 360)) {
            val size = captureDimensions(width, height)
            assertTrue(size.width <= 960 && size.height <= 960)
            assertEquals(0, size.width % 2); assertEquals(0, size.height % 2)
            assertEquals(width.toDouble() / height, size.width.toDouble() / size.height, 0.01)
        }
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidSourceSize() { captureDimensions(0, 720) }
}
