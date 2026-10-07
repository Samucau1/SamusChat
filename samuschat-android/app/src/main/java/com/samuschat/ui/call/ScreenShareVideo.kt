package com.samuschat.ui.call

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.samuschat.BuildConfig
import org.webrtc.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/** TextureView participates in Compose/Dialog composition instead of using a separate surface. */
@Composable
fun ScreenShareVideo(track: VideoTrack, egl: EglBase.Context, modifier: Modifier = Modifier, local: Boolean = false) {
    key(track, egl) {
        var rendered by remember { mutableStateOf(false) }
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { context ->
                    ScreenVideoContainer(context, egl, local) { rendered = true }.apply { track.addSink(video) }
                },
                modifier = Modifier.matchParentSize(),
                onRelease = { container ->
                    try {
                        track.removeSink(container.video)
                    } catch (_: IllegalStateException) {
                        // The engine may have already disposed the track and removed all sinks.
                    } finally {
                        container.video.close()
                    }
                }
            )
            if (!rendered) Text("Aguardando imagem…", color = Color.White)
        }
    }
}

private class ScreenVideoContainer(
    context: Context, egl: EglBase.Context, local: Boolean, onRendered: () -> Unit
) : FrameLayout(context) {
    val video = ScreenVideoTexture(context, egl, local, onRendered)

    init {
        addView(video, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> video.fitWithin(width, height) }
    }
}

private class ScreenVideoTexture(
    context: Context, egl: EglBase.Context, local: Boolean, onRendered: () -> Unit
) : TextureView(context), TextureView.SurfaceTextureListener, VideoSink {
    private val renderer = EglRenderer("SamusScreenTexture")
    private val firstFrame = AtomicBoolean(false)
    @Volatile private var closed = false
    @Volatile private var frameWidth = 0
    @Volatile private var frameHeight = 0

    init {
        isOpaque = false
        renderer.init(egl, EglBase.CONFIG_PLAIN, GlRectDrawer())
        renderer.setMirror(false)
        renderer.addRenderListener {
            if (!closed && firstFrame.compareAndSet(false, true)) {
                if (BuildConfig.DEBUG) android.util.Log.i("ScreenPreview", "firstFrame local=$local")
                post { if (!closed) onRendered() }
            }
        }
        surfaceTextureListener = this
    }

    override fun onFrame(frame: VideoFrame) {
        if (closed) return
        val width = frame.rotatedWidth
        val height = frame.rotatedHeight
        if (width != frameWidth || height != frameHeight) {
            frameWidth = width; frameHeight = height
            post { if (!closed) (parent as? FrameLayout)?.let { fitWithin(it.width, it.height) } }
        }
        renderer.onFrame(frame)
    }

    /** Fit the texture to the video aspect ratio, leaving bars instead of cropping. */
    fun fitWithin(availableWidth: Int, availableHeight: Int) {
        if (closed || availableWidth <= 0 || availableHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) return
        val scale = minOf(availableWidth.toFloat() / frameWidth, availableHeight.toFloat() / frameHeight)
        val targetWidth = maxOf(1, (frameWidth * scale).toInt())
        val targetHeight = maxOf(1, (frameHeight * scale).toInt())
        val params = layoutParams as FrameLayout.LayoutParams
        if (params.width != targetWidth || params.height != targetHeight) {
            params.width = targetWidth; params.height = targetHeight
            layoutParams = params
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (closed) return
        renderer.setLayoutAspectRatio(width.toFloat() / maxOf(1, height))
        renderer.createEglSurface(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (!closed) renderer.setLayoutAspectRatio(width.toFloat() / maxOf(1, height))
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (!closed) {
            val released = CountDownLatch(1)
            renderer.releaseEglSurface { released.countDown() }
            ThreadUtils.awaitUninterruptibly(released)
        }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    fun close() {
        if (closed) return
        closed = true
        renderer.release()
    }
}
