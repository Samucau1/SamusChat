package com.samuschat.ui.call

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.samuschat.BuildConfig
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/** Rebind the renderer when a participant's track or call engine changes. */
@Composable
fun ScreenShareVideo(track: VideoTrack, egl: EglBase.Context, modifier: Modifier = Modifier, local: Boolean = false) {
    key(track, egl) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    init(egl, object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {
                            if (BuildConfig.DEBUG) android.util.Log.i("ScreenPreview", "firstFrame local=$local")
                        }
                        override fun onFrameResolutionChanged(width: Int, height: Int, rotation: Int) {}
                    })
                    setEnableHardwareScaler(true)
                    setMirror(false)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    track.addSink(this)
                }
            },
            modifier = modifier,
            onRelease = { renderer ->
                // An ended call can dispose the track before Compose removes this view.
                // VideoTrack.dispose() already removes its sinks in that case.
                try {
                    track.removeSink(renderer)
                } catch (_: IllegalStateException) {
                    // The track has already been disposed by the call engine.
                } finally {
                    renderer.release()
                }
            }
        )
    }
}
