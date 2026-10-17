package com.samuschat

import com.samuschat.ui.call.SpeakingGate
import org.junit.Assert.*
import org.junit.Test

class SpeakingTest {
    @Test fun silenceAttackReleaseMuteAndResume() {
        val gate = SpeakingGate()
        assertFalse(gate.update(0.0, 0))
        assertFalse(gate.update(Double.NaN, 1))
        assertTrue(gate.update(0.08, 100))
        assertTrue(gate.update(0.0, 499))
        assertFalse(gate.update(0.0, 500))
        assertTrue(gate.update(0.1, 600))
        assertFalse(gate.update(0.1, 601, muted = true))
        assertFalse(gate.update(0.0, 602))
        assertTrue(gate.update(0.1, 603))
    }
}
