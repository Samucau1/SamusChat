package com.samuschat.util

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlin.random.Random

class RecoveryBackoff(private val base: Long = 3000, private val cap: Long = 30000,
                      private val random: () -> Double = { Random.nextDouble() }) {
    private var ceiling = base
    @Synchronized fun next(): Long {
        val result = (ceiling * (0.5 + random() * 0.5)).toLong()
        ceiling = (ceiling * 2).coerceAtMost(cap)
        return result
    }
    @Synchronized fun reset() { ceiling = base }
}

/** One running request and one coalesced trailing request, on the caller's dispatcher. */
class CoalescingRecovery {
    private val mutex = Mutex()
    private var pending = false
    suspend fun run(block: suspend () -> Unit) {
        if (!mutex.tryLock()) { pending = true; return }
        try {
            do { pending = false; block() } while (pending)
        } finally { mutex.unlock() }
    }
}

/** Never replace a connecting socket; bound the handshake and cancel on lifecycle stop. */
suspend fun maintainConnection(connect: suspend (opened: () -> Unit, closed: () -> Unit) -> Unit,
                               disconnect: () -> Unit,
                               backoff: RecoveryBackoff = RecoveryBackoff()) = coroutineScope {
    while (isActive) {
        val opened = CompletableDeferred<Boolean>()
        val closed = CompletableDeferred<Unit>()
        try {
            connect({ opened.complete(true) }, { opened.complete(false); closed.complete(Unit) })
            if (withTimeoutOrNull(10000) { opened.await() } == true) {
                backoff.reset()
                closed.await()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Retry transport failures within the bounded loop. */ }
        finally { disconnect() }
        delay(backoff.next())
    }
}
