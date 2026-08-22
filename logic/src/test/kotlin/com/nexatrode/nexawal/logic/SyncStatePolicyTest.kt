package com.nexatrode.nexawal.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatePolicyTest {
    @Test
    fun `durable restore height repairs a cache raised to tip`() {
        assertEquals(
            3_519_450L,
            ScanRecoveryPolicy.recoveryRestoreHeight(
                coreRestoreHeight = 3_745_389L,
                persistedRestoreHeight = 3_519_450L,
            ),
        )
    }

    @Test
    fun `clean completed scan is not rewound when a new refresh starts`() {
        val decision = ScanRecoveryPolicy.decide(
            previousScanInterrupted = false,
            lastScanned = 3_745_389L,
            chainHeight = 3_745_389L,
            chainTime = 1L,
            restoreHeight = 3_519_450L,
            trustedScannedHeight = 3_745_389L,
            transfersEmpty = false,
            didRewindEmptyHistory = false,
        )

        assertNull(decision)
    }

    @Test
    fun `previously interrupted scan at tip rewinds to trusted checkpoint`() {
        val decision = ScanRecoveryPolicy.decide(
            previousScanInterrupted = true,
            lastScanned = 3_745_389L,
            chainHeight = 3_745_389L,
            chainTime = 1L,
            restoreHeight = 3_519_450L,
            trustedScannedHeight = 3_700_000L,
            transfersEmpty = false,
            didRewindEmptyHistory = false,
        )

        assertEquals(3_700_000L, decision?.rewindHeight)
        assertFalse(decision?.emptyHistoryAtTip ?: true)
    }

    @Test
    fun `cursor ahead of clean checkpoint rewinds even without interrupted marker`() {
        val decision = ScanRecoveryPolicy.decide(
            previousScanInterrupted = false,
            lastScanned = 3_745_389L,
            chainHeight = 3_745_389L,
            chainTime = 1L,
            restoreHeight = 3_519_450L,
            trustedScannedHeight = 3_700_000L,
            transfersEmpty = false,
            didRewindEmptyHistory = false,
        )

        assertEquals(3_700_000L, decision?.rewindHeight)
    }

    @Test
    fun `empty history at tip rewinds to restore height once`() {
        val decision = ScanRecoveryPolicy.decide(
            previousScanInterrupted = false,
            lastScanned = 3_745_389L,
            chainHeight = 3_745_389L,
            chainTime = 1L,
            restoreHeight = 3_519_450L,
            trustedScannedHeight = 3_745_389L,
            transfersEmpty = true,
            didRewindEmptyHistory = false,
        )

        assertEquals(3_519_450L, decision?.rewindHeight)
        assertTrue(decision?.emptyHistoryAtTip ?: false)
    }

    @Test
    fun `scan still behind tip is never rewound before continuing`() {
        val decision = ScanRecoveryPolicy.decide(
            previousScanInterrupted = true,
            lastScanned = 3_600_000L,
            chainHeight = 3_745_389L,
            chainTime = 1L,
            restoreHeight = 3_519_450L,
            trustedScannedHeight = 3_590_000L,
            transfersEmpty = false,
            didRewindEmptyHistory = false,
        )

        assertNull(decision)
    }

    @Test
    fun `intermediate nonzero balance is applied but remains provisional`() {
        val decision = BalanceSnapshotPolicy.decide(
            knownTotal = 0L,
            knownUnlocked = 0L,
            proposedTotal = 7_999_413_881L,
            proposedUnlocked = 7_999_413_881L,
            refreshInProgress = true,
            scanInterrupted = true,
            allowAuthoritativeZero = false,
        )

        assertTrue(decision.apply)
        assertTrue(decision.staleWhileSyncing)
    }

    @Test
    fun `successful finalized zero replaces a provisional nonzero balance`() {
        val decision = BalanceSnapshotPolicy.decide(
            knownTotal = 7_999_413_881L,
            knownUnlocked = 7_999_413_881L,
            proposedTotal = 0L,
            proposedUnlocked = 0L,
            refreshInProgress = false,
            scanInterrupted = false,
            allowAuthoritativeZero = true,
        )

        assertTrue(decision.apply)
        assertFalse(decision.staleWhileSyncing)
    }

    @Test
    fun `interrupted zero does not erase a known nonzero balance`() {
        val decision = BalanceSnapshotPolicy.decide(
            knownTotal = 10L,
            knownUnlocked = 10L,
            proposedTotal = 0L,
            proposedUnlocked = 0L,
            refreshInProgress = false,
            scanInterrupted = true,
            allowAuthoritativeZero = false,
        )

        assertFalse(decision.apply)
        assertTrue(decision.staleWhileSyncing)
    }

    @Test
    fun `steady state nonzero balance is authoritative`() {
        val decision = BalanceSnapshotPolicy.decide(
            knownTotal = 10L,
            knownUnlocked = 10L,
            proposedTotal = 20L,
            proposedUnlocked = 20L,
            refreshInProgress = false,
            scanInterrupted = false,
            allowAuthoritativeZero = false,
        )

        assertTrue(decision.apply)
        assertFalse(decision.staleWhileSyncing)
    }
}
