package com.samuschat.ui.call

import android.os.SystemClock
import com.samuschat.BuildConfig
import org.webrtc.PeerConnection
import org.webrtc.RTCStatsReport
import org.webrtc.RtpSender

internal fun mediaConfiguration(ice: List<IceServerConfig>) =
    PeerConnection.RTCConfiguration(ice.map {
        PeerConnection.IceServer.builder(it.urls).setUsername(it.username).setPassword(it.credential).createIceServer()
    }).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        if (BuildConfig.CALLS_FORCE_RELAY) iceTransportsType = PeerConnection.IceTransportsType.RELAY
    }

// Remote receiver reports describe how our outgoing microphone actually arrived.
internal class VoiceProtection {
    private var nextSample = 0L
    private var bad = 0
    private var good = 0
    private var level = 0
    private var lastTimestamp = -1.0
    fun sample(report: RTCStatsReport, sender: RtpSender, profile: ScreenCaptureProfile?) {
        if (profile == null) { level = 0; bad = 0; good = 0; return }
        val now = SystemClock.elapsedRealtime()
        if (now < nextSample) return
        nextSample = now + 2000
        val audio = report.statsMap.values.firstOrNull { it.type == "remote-inbound-rtp" && it.members["kind"] == "audio" } ?: return
        if (audio.timestampUs.toDouble() == lastTimestamp) return
        lastTimestamp = audio.timestampUs.toDouble()
        val loss = (audio.members["fractionLost"] as? Number)?.toDouble()
        val jitter = (audio.members["jitter"] as? Number)?.toDouble()
        if (loss == null && jitter == null) return
        val pressured = (loss ?: 0.0) >= 0.04 || (jitter ?: 0.0) >= 0.05
        bad = if (pressured) bad + 1 else 0
        good = if (pressured) 0 else good + 1
        val target = when {
            bad >= 3 -> minOf(3, level + 1)
            good >= 15 -> maxOf(0, level - 1)
            else -> level
        }
        if (target != level) {
            configureScreenSender(sender, profile.copy(bitrateBps = profile.bitrateBps / (1 + target)))
            level = target; bad = 0; good = 0
        }
    }
}
