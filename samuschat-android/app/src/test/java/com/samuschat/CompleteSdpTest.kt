package com.samuschat
import com.samuschat.ui.call.*
import org.junit.Assert.*
import org.junit.Test

class CompleteSdpTest {
    @Test fun gatheredCandidatesBelongToTheirMediaSections() {
        val result = completeSdp("v=0\r\nm=audio 9 RTP/SAVPF 111\r\nm=video 9 RTP/SAVPF 96\r\n", listOf(GatheredCandidate(0,"candidate:audio"),GatheredCandidate(1,"candidate:video")))
        assertTrue(result.indexOf("a=candidate:audio") < result.indexOf("m=video"))
        assertTrue(result.indexOf("a=candidate:video") > result.indexOf("m=video"))
        assertTrue(result.endsWith("\r\n"))
    }
    @Test fun repeatedCandidatesAreNotDuplicated() {
        val result = completeSdp("v=0\nm=audio 9 RTP/SAVPF 111\na=candidate:audio\n",List(2){GatheredCandidate(0,"candidate:audio")})
        assertEquals(1,Regex("a=candidate:audio").findAll(result).count())
    }
    @Test fun bundledCandidateIsKeptInEachMediaSection() {
        val result = completeSdp("v=0\nm=audio 9 RTP/SAVPF 111\nm=video 9 RTP/SAVPF 96\n",
            listOf(GatheredCandidate(0, "candidate:shared"), GatheredCandidate(1, "candidate:shared")))
        assertEquals(2, Regex("a=candidate:shared").findAll(result).count())
    }
}
