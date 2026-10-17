package com.samuschat.ui.call

import org.webrtc.RTCStatsReport

/** Short release avoids flicker between syllables. No samples are retained. */
class SpeakingGate {
    private var lastVoice = Long.MIN_VALUE
    fun update(level: Double, now: Long, muted: Boolean = false): Boolean {
        if (muted) { lastVoice = Long.MIN_VALUE; return false }
        if (level.isFinite() && level >= 0.02) lastVoice = now
        return lastVoice != Long.MIN_VALUE && now - lastVoice < 400
    }
}

internal fun audioLevels(report: RTCStatsReport): Pair<Double, Double> {
    fun level(type: String) = report.statsMap.values.filter {
        it.type == type && (it.members["kind"] == "audio" || it.members["mediaType"] == "audio")
    }.maxOfOrNull { (it.members["audioLevel"] as? Number)?.toDouble() ?: 0.0 } ?: 0.0
    return level("media-source") to level("inbound-rtp")
}
