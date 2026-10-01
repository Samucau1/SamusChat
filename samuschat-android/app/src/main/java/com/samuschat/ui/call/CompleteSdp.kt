package com.samuschat.ui.call

data class GatheredCandidate(val mediaIndex: Int, val value: String)

/** REST signaling sends a complete SDP, including every gathered ICE candidate. */
fun completeSdp(description: String, candidates: List<GatheredCandidate>): String {
    val lines = description.replace("\r\n", "\n").trimEnd().split('\n')
    val result = mutableListOf<String>()
    var index = -1
    var mediaStart = 0
    fun appendCandidates() {
        if (index < 0) return
        candidates.filter { it.mediaIndex == index }.map { "a=${it.value}" }.distinct().forEach {
            if (it !in result.subList(mediaStart, result.size)) result.add(it)
        }
    }
    lines.forEach { line ->
        if (line.startsWith("m=")) { appendCandidates(); index++; mediaStart = result.size }
        result.add(line)
    }
    appendCandidates()
    return result.joinToString("\r\n", postfix = "\r\n")
}
