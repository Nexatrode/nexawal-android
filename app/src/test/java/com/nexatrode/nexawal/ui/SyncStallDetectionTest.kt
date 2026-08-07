package com.nexatrode.nexawal.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStallDetectionTest {

    @Test
    fun stallFlag_or_legacyMessage() {
        assertTrue(isSyncStallError(syncStalled = true, errorMessage = null))
        assertTrue(isSyncStallError(syncStalled = false, errorMessage = "Refresh stalled (>90s)"))
        assertFalse(isSyncStallError(syncStalled = false, errorMessage = "Network error"))
    }

    @Test
    fun stallFlag_takesPrecedenceEvenWithoutLegacyMessage() {
        assertTrue(isSyncStallError(syncStalled = true, errorMessage = "Unrelated error"))
    }

    @Test
    fun noStall_whenNeitherFlagNorMessageIndicatesIt() {
        assertFalse(isSyncStallError(syncStalled = false, errorMessage = null))
    }
}
