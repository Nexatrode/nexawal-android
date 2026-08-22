package com.nexatrode.nexawal.logic

/** A rewind that should happen before starting the next wallet refresh. */
data class ScanRecoveryDecision(
    val rewindHeight: Long,
    val emptyHistoryAtTip: Boolean,
)

/**
 * Decides whether persisted scan state represents an interrupted/incomplete refresh.
 *
 * [previousScanInterrupted] must describe the state left by the previous refresh. It must not be
 * the marker written for the refresh that is about to start.
 */
object ScanRecoveryPolicy {
    /** A persisted restore choice may repair a cache whose internal restore height was raised. */
    fun recoveryRestoreHeight(
        coreRestoreHeight: Long,
        persistedRestoreHeight: Long?,
    ): Long = minOf(
        coreRestoreHeight,
        persistedRestoreHeight?.takeIf { it >= 0L } ?: coreRestoreHeight,
    )

    fun decide(
        previousScanInterrupted: Boolean,
        lastScanned: Long,
        chainHeight: Long,
        chainTime: Long,
        restoreHeight: Long,
        trustedScannedHeight: Long,
        transfersEmpty: Boolean,
        didRewindEmptyHistory: Boolean,
        tolerance: Long = 3L,
        historySpanThreshold: Long = 10_000L,
    ): ScanRecoveryDecision? {
        require(tolerance >= 0L) { "tolerance must be >= 0" }
        require(historySpanThreshold >= 0L) { "historySpanThreshold must be >= 0" }

        val tipKnown = chainHeight > restoreHeight || chainTime > 0L
        if (!tipKnown) return null
        if (safeAdd(lastScanned, tolerance) < chainHeight) return null

        val aheadOfCheckpoint = lastScanned > safeAdd(trustedScannedHeight, tolerance)
        val emptyHistoryAtTip =
            !didRewindEmptyHistory &&
                chainHeight > safeAdd(restoreHeight, historySpanThreshold) &&
                transfersEmpty

        if (!previousScanInterrupted && !aheadOfCheckpoint && !emptyHistoryAtTip) return null

        return ScanRecoveryDecision(
            rewindHeight = if (emptyHistoryAtTip) {
                restoreHeight
            } else {
                maxOf(restoreHeight, trustedScannedHeight)
            },
            emptyHistoryAtTip = emptyHistoryAtTip,
        )
    }

    private fun safeAdd(value: Long, increment: Long): Long =
        if (value > Long.MAX_VALUE - increment) Long.MAX_VALUE else value + increment
}

/** How a balance snapshot should affect the balance currently presented to the user. */
data class BalanceSnapshotDecision(
    val apply: Boolean,
    val staleWhileSyncing: Boolean,
)

object BalanceSnapshotPolicy {
    fun decide(
        knownTotal: Long,
        knownUnlocked: Long,
        proposedTotal: Long,
        proposedUnlocked: Long,
        refreshInProgress: Boolean,
        scanInterrupted: Boolean,
        allowAuthoritativeZero: Boolean,
    ): BalanceSnapshotDecision {
        val hasKnownNonZero = knownTotal > 0L || knownUnlocked > 0L
        val proposedZero = proposedTotal == 0L && proposedUnlocked == 0L

        if (proposedZero && hasKnownNonZero && !allowAuthoritativeZero) {
            return BalanceSnapshotDecision(
                apply = false,
                staleWhileSyncing = true,
            )
        }

        return BalanceSnapshotDecision(
            apply = true,
            staleWhileSyncing =
                !allowAuthoritativeZero && (refreshInProgress || scanInterrupted),
        )
    }
}
