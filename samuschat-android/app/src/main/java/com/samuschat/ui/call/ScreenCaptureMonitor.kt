package com.samuschat.ui.call

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager

data class CaptureDimensions(val width: Int, val height: Int)

fun captureDimensions(width: Int, height: Int, profile: ScreenCaptureProfile = ScreenCaptureProfile.BALANCED): CaptureDimensions {
    require(width > 0 && height > 0)
    // Preserve a 720p phone's native pixels instead of reducing it to 456p.
    // Cap larger devices by both axes, without upscaling or stretching.
    val scale = minOf(1.0, profile.shortEdge.toDouble() / minOf(width, height), profile.longEdge.toDouble() / maxOf(width, height))
    return CaptureDimensions(maxOf(2, (width * scale).toInt() / 2 * 2), maxOf(2, (height * scale).toInt() / 2 * 2))
}

const val SCREEN_CAPTURE_FPS = 30
fun configureScreenSender(sender: org.webrtc.RtpSender, profile: ScreenCaptureProfile = ScreenCaptureProfile.BALANCED) {
    val parameters = sender.parameters
    if (parameters.encodings.isEmpty()) return
    parameters.encodings.forEach { encoding ->
        encoding.maxFramerate = profile.fps
        encoding.maxBitrateBps = profile.bitrateBps
        encoding.scaleResolutionDownBy = 1.0
    }
    parameters.degradationPreference = org.webrtc.RtpParameters.DegradationPreference.BALANCED
    if (!sender.setParameters(parameters) && com.samuschat.BuildConfig.DEBUG) android.util.Log.w("ScreenCapture", "Encoder kept native parameters")
}

/** Display changes work while another app is visible, including forced game rotation on Android 10. */
class ScreenCaptureMonitor(context: Context, private val resized: (CaptureDimensions) -> Unit) : DisplayManager.DisplayListener {
    // Query once per sharing session, not on every frame or stats sample.
    val profile: ScreenCaptureProfile = context.getSystemService(android.app.ActivityManager::class.java).let { manager ->
        val memory = android.app.ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        screenCaptureProfile(manager.isLowRamDevice, memory.totalMem, memory.lowMemory)
    }
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val windows = context.getSystemService(WindowManager::class.java)
    var dimensions = displayDimensions()
        private set
    private var started = false

    @Suppress("DEPRECATION")
    private fun displayDimensions(): CaptureDimensions {
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windows.maximumWindowMetrics.bounds
            return captureDimensions(bounds.width(), bounds.height(), profile)
        }
        val metrics = DisplayMetrics()
        checkNotNull(displays.getDisplay(Display.DEFAULT_DISPLAY)).getRealMetrics(metrics)
        return captureDimensions(metrics.widthPixels, metrics.heightPixels, profile)
    }
    fun start() {
        if (started) return
        started = true
        // On 14+, projection callbacks provide the actual selected app window size.
        if (Build.VERSION.SDK_INT < 34) displays.registerDisplayListener(this, Handler(Looper.getMainLooper()))
    }
    fun capturedContent(width: Int, height: Int) {
        if (width > 0 && height > 0 && started) update(captureDimensions(width, height, profile))
    }
    private fun update(next: CaptureDimensions) {
        if (next == dimensions || !started) return
        dimensions = next
        resized(next)
    }
    override fun onDisplayChanged(id: Int) {
        if (id == Display.DEFAULT_DISPLAY && started) update(displayDimensions())
    }
    override fun onDisplayAdded(id: Int) {}
    override fun onDisplayRemoved(id: Int) {}
    fun stop() {
        if (!started) return
        started = false
        if (Build.VERSION.SDK_INT < 34) displays.unregisterDisplayListener(this)
    }
}
