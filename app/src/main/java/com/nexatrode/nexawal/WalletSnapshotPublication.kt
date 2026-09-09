package com.nexatrode.nexawal

import com.nexatrode.nexawal.walletcore.WalletCore
import java.util.UUID

/** The final publisher used by WalletManager, separate from guarded mid-scan reads. */
internal object WalletSnapshotPublication {
    fun sameReadSession(read: WalletManager.UiState, current: WalletManager.UiState): Boolean =
        read.walletId == current.walletId && read.walletAddress == current.walletAddress &&
            read.historySession == current.historySession && read.snapshotEpoch == current.snapshotEpoch

    /** Call only after successful native completion and fresh idle status/history/balance reads. */
    fun completed(
        previous: WalletManager.UiState,
        status: WalletCore.SyncStatus,
        history: HistoryPage,
        balance: WalletCore.Balance,
        now: Long,
    ): WalletManager.UiState {
        require(history.walletId == previous.walletId) { "Final snapshot belongs to another wallet" }
        require(status.chainHeight > 0 && status.lastScanned >= status.chainHeight) { "Final scan is not at tip" }
        require(history.lastScannedHeight == status.lastScanned && history.chainHeight == status.chainHeight) {
            "Final history and scan checkpoint do not match"
        }
        // Publish the fresh result, not retained UI counts. Empty is valid after a reorg,
        // and the old spinner/interruption marker must not veto this completed snapshot.
        return previous.copy(
            refreshInProgress = false, refreshStartedAtMs = null, refreshLastProgressAtMs = null,
            syncStatus = status, lastError = null, syncStalled = false, refreshTargetHeight = null,
            gapLimit = null, accountGap = null,
            transfersJson = null, transfers = history.transfers, totalHistoryCount = history.totalCount,
            pendingHistoryCount = history.pendingCount, historyRevision = history.revision,
            transfersParseError = null, lastTransfersRefreshAtMs = now,
            balance = balance, balanceIsStaleWhileSyncing = false, lastBalanceRefreshAtMs = now,
            snapshotEpoch = UUID.randomUUID().toString(),
        )
    }
}
