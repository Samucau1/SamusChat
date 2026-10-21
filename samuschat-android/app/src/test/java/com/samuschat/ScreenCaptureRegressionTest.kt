package com.samuschat

import com.samuschat.ui.call.SCREEN_CAPTURE_FPS
import com.samuschat.ui.call.captureDimensions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Golden device matrix: detect the former 960px reduction and accidental upscaling. */
@RunWith(Parameterized::class)
class ScreenCaptureRegressionTest(
    private val width: Int, private val height: Int,
    private val expectedWidth: Int, private val expectedHeight: Int
) {
    @Test fun nativePixelsOrientationAndWorkloadStayWithinValidatedBudget() {
        val size = captureDimensions(width, height)
        assertEquals(expectedWidth, size.width)
        assertEquals(expectedHeight, size.height)
        assertTrue("Never upscale the source", size.width <= width && size.height <= height)
        assertTrue("Frame rate exceeds validated limit", SCREEN_CAPTURE_FPS in 15..30)
        assertTrue("Pixel workload exceeds validated ceiling",
            size.width.toLong() * size.height * SCREEN_CAPTURE_FPS <= 900L * 1920 * 30)
        val rotated = captureDimensions(height, width)
        assertEquals(size.width, rotated.height)
        assertEquals(size.height, rotated.width)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1} -> {2}x{3}")
        fun devices() = listOf(
            arrayOf(720, 1512, 720, 1512), arrayOf(1512, 720, 1512, 720),
            arrayOf(640, 360, 640, 360), arrayOf(1080, 1920, 900, 1600),
            arrayOf(1080, 2400, 864, 1920), arrayOf(3840, 2160, 1600, 900)
        )
    }
}
