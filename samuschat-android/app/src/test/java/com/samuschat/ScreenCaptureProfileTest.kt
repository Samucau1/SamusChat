package com.samuschat

import com.samuschat.ui.call.*
import org.junit.Assert.*
import org.junit.Test

class ScreenCaptureProfileTest {
    @Test fun lowMemoryPhoneBalancesPixelsAtRequested24Fps() {
        val profile = screenCaptureProfile(false, 2L * 1024 * 1024 * 1024)
        val size = captureDimensions(720, 1512, profile)
        assertEquals(CaptureDimensions(640, 1344), size)
        assertEquals(24, profile.fps)
        assertTrue(profile.bitrateBps <= 2_000_000)
        assertTrue(size.width.toLong() * size.height * profile.fps <= 20_643_840L)
    }
    @Test fun lowRamFlagAndMemoryPressureOverrideLargePhysicalMemory() {
        val highMemory = 8L * 1024 * 1024 * 1024
        assertEquals(ScreenCaptureProfile.ECONOMICAL, screenCaptureProfile(true, highMemory))
        assertEquals(ScreenCaptureProfile.ECONOMICAL, screenCaptureProfile(false, highMemory, true))
        assertEquals(ScreenCaptureProfile.ECONOMICAL, screenCaptureProfile(false, 0))
        assertEquals(ScreenCaptureProfile.BALANCED, screenCaptureProfile(false, highMemory))
    }
    @Test fun allProfilesPreserveRotationAndBoundLargeAndSmallSources() {
        for (profile in listOf(ScreenCaptureProfile.ECONOMICAL, ScreenCaptureProfile.BALANCED)) {
            for ((w, h) in listOf(640 to 360, 720 to 1512, 1080 to 2400, 3840 to 2160)) {
                val size = captureDimensions(w, h, profile)
                assertEquals(CaptureDimensions(size.height, size.width), captureDimensions(h, w, profile))
                assertTrue(size.width <= w && size.height <= h)
                assertTrue(maxOf(size.width, size.height) <= profile.longEdge)
                assertTrue(minOf(size.width, size.height) <= profile.shortEdge)
                assertEquals(w.toDouble()/h, size.width.toDouble()/size.height, 0.01)
            }
        }
    }
}
