package com.nexatrode.nexawal.logic

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SessionCleanupPolicyTest {
    @Test fun closeArrivingDuringRetryMustBeReplayedAndCannotBeDowngraded() {
        val requests = SessionCleanupRequests()
        requests.request(close = false, continueRefresh = true)
        val sealing = requests.snapshot()
        requests.request(close = true, continueRefresh = false)
        requests.request(close = false, continueRefresh = true)
        assertNull(requests.finish(sealing.generation))
        val closing = requests.snapshot()
        assertTrue(closing.close)
        assertFalse(closing.continueRefresh)
        assertNotNull(requests.finish(closing.generation))
        assertFalse(requests.snapshot().close)
    }

    @Test fun concurrentCleanupRequestsPreserveCloseAndOnlyCurrentGenerationCanFinish() {
        val requests = SessionCleanupRequests()
        val workers = (0 until 50).map { index -> Thread {
            requests.request(close = index == 25, continueRefresh = true)
        }.apply { start() } }
        workers.forEach { it.join() }
        val pending = requests.snapshot()
        assertEquals(50, pending.generation)
        assertTrue(pending.close)
        assertFalse(pending.continueRefresh)
        assertNull(requests.finish(pending.generation - 1))
        assertNotNull(requests.finish(pending.generation))
    }

    @Test fun retriesUntilNativeCleanupActuallyCompletes() = runTest {
        var attempts = 0
        SessionCleanupPolicy.complete(1) {
            attempts++
            if (attempts < 4) throw RuntimeException("wallet_seal (rc=-31): busy")
        }
        assertEquals(4, attempts)
    }

    @Test fun doesNotRetryOtherErrorsOrUntrustedDiagnosticSubstring() = runTest {
        for (message in listOf("wallet_seal (rc=-1): broken", "RPC error wallet_close (rc=-31): busy")) {
            var attempts = 0
            try {
                SessionCleanupPolicy.complete(1) { attempts++; throw IllegalStateException(message) }
                fail("Cleanup falsely reported success")
            } catch (_: IllegalStateException) { assertEquals(1, attempts) }
        }
        assertTrue(SessionCleanupPolicy.isTimeout(IllegalStateException("wallet_close (rc=-31): busy")))
        assertTrue(SessionCleanupPolicy.isTimeout(RuntimeException("wallet_seal (rc=-31)")))
    }
}
