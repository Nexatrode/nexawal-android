package com.nexatrode.nexawal.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RecoveryResetTest {
    @Test fun canceledOrStaleAuthenticationDoesNotWipeStoredData() = runBlocking {
        for (stale in listOf(false, true)) {
            val authority = SessionAuthorityEpoch()
            val token = authority.token()
            var reset = false
            try {
                RecoveryReset.run(authority, token, authenticate = {
                    if (stale) authority.invalidate() else throw CancellationException()
                }, reset = { reset = true }, foreground = { true }, openSetup = { fail("unexpected setup") })
                fail("stale/canceled approval accepted")
            } catch (_: CancellationException) {}
            assertFalse(reset)
        }
    }
    @Test fun inaccessibleSeedCanBeResetWithoutDecryptingButCleanupFailureDoesNotOpenSetup() = runBlocking {
        val authority = SessionAuthorityEpoch()
        val events = mutableListOf<String>()
        try {
            RecoveryReset.run(authority, authority.token(), { events += "auth" },
                { events += "cleanup"; error("archive failed") }, { true }, { events += "open" })
        } catch (_: IllegalStateException) {}
        assertEquals(listOf("auth", "cleanup"), events)
    }
    @Test fun successfulResetOpensOnlyAnEmptyForegroundSetup() = runBlocking {
        for (foreground in listOf(false, true)) {
            val authority = SessionAuthorityEpoch()
            var resets = 0; var opens = 0
            RecoveryReset.run(authority, authority.token(), {}, { resets++ }, { foreground }, { opens++ })
            assertEquals(1, resets)
            assertEquals(if (foreground) 1 else 0, opens)
        }
    }
}
