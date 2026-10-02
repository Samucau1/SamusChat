package com.samuschat

import com.samuschat.ui.call.*
import org.junit.Test
import org.junit.Assert.*

class CallPolicyTest {
    private val call = CallSession("id", "a", "b", "RINGING", null, null)
    @Test fun onlyRecipientCanAcceptRingingCall() {
        assertTrue(CallPolicy.mayAccept(call, "b"))
        assertFalse(CallPolicy.mayAccept(call, "a"))
        assertFalse(CallPolicy.mayAccept(call.copy(state = "ACTIVE"), "b"))
        assertFalse(CallPolicy.mayAccept(null, "b"))
    }
    @Test fun rejectsDuplicateScreenSharingAndDisconnectedSharing() {
        assertTrue(CallPolicy.mayShare(true, false))
        assertFalse(CallPolicy.mayShare(true, true))
        assertFalse(CallPolicy.mayShare(false, false))
    }
    @Test fun deviceAudioHasStrictPacketAndQueueBounds() {
        assertTrue(CallPolicy.validAudioPacket(640))
        for (size in listOf(0, 1, 639, 641, 65536)) assertFalse(CallPolicy.validAudioPacket(size))
        assertTrue(CallPolicy.mayQueueAudio(0))
        assertFalse(CallPolicy.mayQueueAudio(3200))
    }
    @Test fun terminalStatesAndPeerAreCorrect() {
        assertEquals("b", call.peer("a"))
        assertEquals("a", call.peer("b"))
        for (state in listOf("ENDED", "DECLINED", "EXPIRED")) assertTrue(call.copy(state = state).ended)
        assertFalse(call.ended)
    }
}
