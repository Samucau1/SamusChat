package com.samuschat

import com.samuschat.ui.components.avatarRect
import org.junit.Assert.*
import org.junit.Test

class AvatarCropTest {
    @Test fun portraitLandscapeSquareAndZoomStayWithinSourceWithoutStretching() {
        for ((width, height) in listOf(800 to 400, 400 to 800, 512 to 512)) {
            for (zoom in listOf(1f, 2f, 4f)) for (x in listOf(0f, 0.5f, 1f)) for (y in listOf(0f, 0.5f, 1f)) {
                val crop = avatarRect(width, height, zoom, x, y)
                assertEquals(minOf(width, height) / zoom, crop.side, 0.001f)
                assertTrue(crop.left >= 0 && crop.top >= 0)
                assertTrue(crop.left + crop.side <= width && crop.top + crop.side <= height)
            }
        }
        val right = avatarRect(800, 400, 2f, 1f, 0f)
        assertEquals(600f, right.left); assertEquals(0f, right.top); assertEquals(200f, right.side)
    }
    @Test fun clampsOutOfRangeAndRejectsInvalidGeometry() {
        assertEquals(avatarRect(800, 400, 1f, 0f, 1f), avatarRect(800, 400, -1f, -2f, 2f))
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY)) {
            try { avatarRect(10, 10, invalid, 0f, 0f); fail("Invalid zoom accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
