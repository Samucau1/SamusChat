package com.samuschat.ui.call

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import com.samuschat.BuildConfig
import org.webrtc.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/** TextureView participates in Compose/Dialog composition instead of using a separate surface. */
@Composable
fun ScreenShareVideo(track: VideoTrack, egl: EglBase.Context, modifier: Modifier = Modifier, local: Boolean = false, name: String = "Participante", avatarUrl: String? = null, speaking: Boolean = false) {
    key(track, egl) {
        var fullscreen by rememberSaveable { mutableStateOf(false) }
        var controls by remember { mutableStateOf(false) }
        var fill by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(fullscreen, controls) {
            if (fullscreen && controls) { delay(4000); controls = false }
        }
        if (fullscreen) {
            Dialog(onDismissRequest = { fullscreen = false; controls = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                val view = LocalView.current
                DisposableEffect(view) {
                    val window = (view.parent as? DialogWindowProvider)?.window
                    window?.let {
                        WindowInsetsControllerCompat(it, view).apply {
                            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            hide(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                    onDispose { }
                }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ScreenVideoContent(track, egl, Modifier.fillMaxSize(), local, fill) { controls = !controls }
                    Surface(Modifier.align(Alignment.TopStart).padding(16.dp), color = Color.Black.copy(alpha = 0.7f)) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.samuschat.ui.components.InitialAvatar(name, size = 32.dp, url = avatarUrl)
                            Text(if (speaking) "$name • Falando" else name, color = if (speaking) CallGreen else Color.White)
                        }
                    }
                    if (controls) {
                        Surface(Modifier.align(Alignment.TopEnd).padding(16.dp), color = Color.Black.copy(alpha = 0.7f)) {
                            IconButton(onClick = { fullscreen = false; controls = false }) { Icon(exitFullscreenIcon, "Sair da tela cheia", tint = Color.White) }
                        }
                        Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), color = Color.Black.copy(alpha = 0.7f)) {
                            TextButton(onClick = { fill = !fill }) { Text(if (fill) "Mostrar imagem inteira" else "Preencher tela (corta bordas)", color = Color.White) }
                        }
                    }
                }
            }
        } else {
            Box(modifier) {
                ScreenVideoContent(track, egl, Modifier.matchParentSize(), local, false) { controls = !controls }
                if (controls) Surface(Modifier.align(Alignment.Center).size(64.dp), color = Color.Black.copy(alpha = 0.7f), shape = androidx.compose.foundation.shape.CircleShape) {
                    IconButton(onClick = { fullscreen = true; controls = true }) { Icon(enterFullscreenIcon, "Assistir em tela cheia", tint = Color.White) }
                }
            }
        }
    }
}

private val enterFullscreenIcon = ImageVector.Builder("Fullscreen", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(9f, 3f); lineTo(3f, 3f); lineTo(3f, 9f)
        moveTo(15f, 3f); lineTo(21f, 3f); lineTo(21f, 9f)
        moveTo(3f, 15f); lineTo(3f, 21f); lineTo(9f, 21f)
        moveTo(15f, 21f); lineTo(21f, 21f); lineTo(21f, 15f)
    }
}.build()
private val exitFullscreenIcon = ImageVector.Builder("ExitFullscreen", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(3f, 9f); lineTo(9f, 9f); lineTo(9f, 3f)
        moveTo(15f, 3f); lineTo(15f, 9f); lineTo(21f, 9f)
        moveTo(3f, 15f); lineTo(9f, 15f); lineTo(9f, 21f)
        moveTo(15f, 21f); lineTo(15f, 15f); lineTo(21f, 15f)
    }
}.build()

@Composable
private fun ScreenVideoContent(track: VideoTrack, egl: EglBase.Context, modifier: Modifier, local: Boolean, fill: Boolean, onTap: () -> Unit) {
    key(track, egl) {
        var rendered by remember { mutableStateOf(false) }
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { context ->
                    ScreenVideoContainer(context, egl, local) { rendered = true }.apply { track.addSink(video); video.fill = fill }
                },
                update = { container -> container.video.fill = fill; container.video.fitWithin(container.width, container.height) },
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
            // Intercept touches above the native TextureView, which can otherwise
            // consume the gesture before the Compose parent receives it.
            Box(Modifier.matchParentSize().semantics {
                contentDescription = if (local) "Prévia da transmissão" else "Transmissão recebida"
            }.clickable(onClickLabel = "Mostrar controles da transmissão", onClick = onTap))
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
    var fill = false

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

    /** Fit preserves the whole frame; optional fill crops edges without distorting it. */
    fun fitWithin(availableWidth: Int, availableHeight: Int) {
        if (closed || availableWidth <= 0 || availableHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) return
        val scale = if (fill) maxOf(availableWidth.toFloat() / frameWidth, availableHeight.toFloat() / frameHeight)
            else minOf(availableWidth.toFloat() / frameWidth, availableHeight.toFloat() / frameHeight)
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
