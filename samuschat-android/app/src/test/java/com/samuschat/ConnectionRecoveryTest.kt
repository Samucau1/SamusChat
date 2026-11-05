package com.samuschat

import com.samuschat.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionRecoveryTest {
    @Test fun delayGrowsWithJitterCapsAndResets() {
        val backoff = RecoveryBackoff(random = { 0.0 })
        assertEquals(listOf(1500L,3000L,6000L,12000L,15000L,15000L), List(6) { backoff.next() })
        backoff.reset(); assertEquals(1500L, backoff.next())
        assertEquals(2999L, RecoveryBackoff(random = { 0.999999 }).next())
    }

    @Test fun requestsNeverOverlapAndBurstsBecomeOneTrailingRequest() = runTest {
        val recovery = CoalescingRecovery()
        val release = CompletableDeferred<Unit>()
        var calls = 0; var active = 0; var max = 0
        val work: suspend () -> Unit = { calls++; active++; max = maxOf(max,active); release.await(); active-- }
        val first = launch { recovery.run(work) }; runCurrent()
        repeat(20) { launch { recovery.run(work) } }; runCurrent()
        assertEquals(1,calls)
        release.complete(Unit); first.join()
        assertEquals(2,calls); assertEquals(1,max)
    }

    @Test fun connectingSocketIsNotReplacedBeforeTimeoutAndCancellationStopsRetries() = runTest {
        var attempts=0; var disconnects=0
        val job=launch { maintainConnection({ _, _ -> attempts++ }, { disconnects++ }, RecoveryBackoff(random={0.0})) }
        runCurrent(); advanceTimeBy(9999); runCurrent()
        assertEquals(1,attempts); assertEquals(0,disconnects)
        advanceTimeBy(1); runCurrent(); assertEquals(1,disconnects)
        advanceTimeBy(1500); runCurrent(); assertEquals(2,attempts)
        job.cancelAndJoin(); val before=attempts
        advanceTimeBy(60000); runCurrent(); assertEquals(before,attempts)
    }

    @Test fun successfulConnectionWaitsForCloseAndResetsBackoff() = runTest {
        var attempts=0; var close: () -> Unit = {}
        val job=launch { maintainConnection({ opened, closed -> attempts++; close=closed; opened() }, {}, RecoveryBackoff(random={0.0})) }
        runCurrent(); advanceTimeBy(60000); runCurrent(); assertEquals(1,attempts)
        close(); runCurrent(); advanceTimeBy(1500); runCurrent(); assertEquals(2,attempts)
        close(); runCurrent(); advanceTimeBy(1500); runCurrent(); assertEquals(3,attempts)
        job.cancelAndJoin()
    }
}
