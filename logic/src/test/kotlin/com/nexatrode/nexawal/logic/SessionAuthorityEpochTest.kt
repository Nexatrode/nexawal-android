package com.nexatrode.nexawal.logic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SessionAuthorityEpochTest {
    @Test fun invalidationBeforePublicationPreventsNativeRestore() {
        val epoch = SessionAuthorityEpoch()
        val token = epoch.token()
        epoch.invalidate()
        var opened = false
        try { epoch.withCurrent(token) { opened = true }; fail("stale token") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        assertFalse(opened)
        epoch.withCurrent(epoch.token()) { opened = true }
        assertTrue(opened)
    }
    @Test fun backgroundDuringAuthenticationNeverInvokesOpen() = runTest {
        val gate = WalletAccessGate()
        val release = CompletableDeferred<Unit>()
        var opened = false
        val job = launch { gate.unlock({ release.await() }, { opened = true }) }
        testScheduler.runCurrent()
        gate.lock(); release.complete(Unit); job.join()
        assertFalse(opened)
        assertEquals(WalletAccessGate.State.LOCKED, gate.state.value)
    }
    @Test fun backgroundDuringOpenOwnsCleanupAndCannotPublish() = runTest {
        val gate = WalletAccessGate()
        val release = CompletableDeferred<Unit>()
        var authority = false
        val job = launch { gate.unlock({}, { release.await(); authority = true }, { authority = false }) }
        testScheduler.runCurrent()
        gate.lock(); release.complete(Unit); job.join()
        assertFalse(authority)
        assertEquals(WalletAccessGate.State.LOCKED, gate.state.value)
    }
    @Test fun staleAuthenticationCannotInvalidateNewUnlock() = runTest {
        val gate = WalletAccessGate()
        val release = CompletableDeferred<Unit>()
        val old = launch { gate.unlock({ release.await() }, { fail("old must not open") }) }
        testScheduler.runCurrent()
        gate.lock()
        gate.unlock({}, {})
        release.complete(Unit); old.join()
        assertEquals(WalletAccessGate.State.OPEN, gate.state.value)
    }
}
