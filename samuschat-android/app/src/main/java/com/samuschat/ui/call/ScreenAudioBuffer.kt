package com.samuschat.ui.call

import java.util.TreeMap

/** A packet contains 20 ms of screen sound. All times below are monotonic milliseconds. */
internal class ScreenAudioBuffer {
    private data class Entry(val data: ByteArray, val arrival: Long)
    private val packets = TreeMap<Long, Entry>()
    private var lastPlayed = -1L
    private var firstArrival = 0L

    fun offer(sequence: Long, data: ByteArray, now: Long) {
        if (sequence <= lastPlayed || packets.containsKey(sequence)) return
        if (packets.isEmpty()) firstArrival = now
        packets[sequence] = Entry(data, now)
        if (packets.size > 10) packets.pollFirstEntry()
    }

    fun poll(now: Long): ByteArray? {
        val first = packets.firstEntry() ?: return null
        // Prime playback on startup. Later, wait only when a sequence is missing.
        if (lastPlayed < 0 && now - firstArrival < 60) return null
        if (lastPlayed >= 0 && first.key != lastPlayed + 1 &&
            packets.size < 3 && now - first.value.arrival < 40) return null
        lastPlayed = first.key
        return packets.pollFirstEntry().value.data
    }

    fun isEmpty() = packets.isEmpty()
}
