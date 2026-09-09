package com.nexatrode.nexawal.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WalletAccessGateTest {
    @Test fun cancellationAfterAuthSuccessStillCannotDisableProtection() = runTest {
        var persisted = true
        val job = launch {
            DeviceAuthSettings.update(true, false, { currentCoroutineContext().cancel() }) { persisted = it }
        }
        job.join()
        assertTrue(persisted)
    }

    @Test fun cancelledDisableDoesNotPersist() = runTest {
        var persisted = true
        try {
            DeviceAuthSettings.update(true, false, { throw CancellationException() }) { persisted = it }
            fail("cancelled authentication must fail")
        } catch (_: CancellationException) { }
        assertTrue(persisted)
    }

    @Test fun unavailableDisableDoesNotPersist() = runTest {
        var persisted = true
        try {
            DeviceAuthSettings.update(true, false, { error("unavailable") }) { persisted = it }
            fail("unavailable authentication must fail")
        } catch (_: IllegalStateException) { }
        assertTrue(persisted)
    }

    @Test fun disablingAuthenticatesBeforePersisting() = runTest {
        val events = mutableListOf<String>()
        DeviceAuthSettings.update(true, false, { events += "auth" }) { events += "persist:$it" }
        assertEquals(listOf("auth", "persist:false"), events)
        DeviceAuthSettings.update(false, true, { fail("enabling does not downgrade protection") }) { }
    }

    @Test fun recreationAndCancellationCannotExposeRetainedWallet() = runTest {
        val oldActivity = WalletAccessGate()
        oldActivity.unlock({}, {})
        assertEquals(WalletAccessGate.State.OPEN, oldActivity.state.value)
        val recreatedActivity = WalletAccessGate()
        assertEquals(WalletAccessGate.State.LOCKED, recreatedActivity.state.value)
        try {
            recreatedActivity.unlock({ throw CancellationException() }, { fail("must not open") })
        } catch (_: CancellationException) { }
        assertEquals(WalletAccessGate.State.LOCKED, recreatedActivity.state.value)
        recreatedActivity.unlock({}, {})
        assertEquals(WalletAccessGate.State.OPEN, recreatedActivity.state.value)
    }

    @Test fun walletOpenFailureStaysLocked() = runTest {
        val gate = WalletAccessGate()
        try { gate.unlock({}, { error("cache unavailable") }) } catch (_: IllegalStateException) { }
        assertEquals(WalletAccessGate.State.LOCKED, gate.state.value)
    }

    @Test fun concurrentUnlockDoesNotBypassPendingAuthentication() = runTest {
        val gate = WalletAccessGate()
        val release = CompletableDeferred<Unit>()
        val job = launch { gate.unlock({ release.await() }, {}) }
        testScheduler.runCurrent()
        gate.unlock({ fail("duplicate prompt") }, { fail("duplicate open") })
        assertEquals(WalletAccessGate.State.UNLOCKING, gate.state.value)
        release.complete(Unit)
        job.join()
        assertEquals(WalletAccessGate.State.OPEN, gate.state.value)
    }
}
