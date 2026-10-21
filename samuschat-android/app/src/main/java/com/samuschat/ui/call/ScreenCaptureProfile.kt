package com.samuschat.ui.call

/** Conservative device budgets; WebRTC still adapts to CPU/network pressure. */
data class ScreenCaptureProfile(val shortEdge: Int, val longEdge: Int, val fps: Int, val bitrateBps: Int) {
    companion object {
        val ECONOMICAL = ScreenCaptureProfile(640, 1440, 24, 2_000_000)
        val BALANCED = ScreenCaptureProfile(900, 1920, 24, 3_000_000)
    }
}

fun screenCaptureProfile(lowRam: Boolean, totalMemoryBytes: Long, lowMemory: Boolean = false): ScreenCaptureProfile =
    if (lowRam || lowMemory || totalMemoryBytes <= 3L * 1024 * 1024 * 1024) ScreenCaptureProfile.ECONOMICAL
    else ScreenCaptureProfile.BALANCED
