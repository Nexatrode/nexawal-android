package com.nexatrode.nexawal

import com.nexatrode.nexawal.walletcore.WalletCore
import org.junit.Assert.*
import org.junit.Test

class WalletSnapshotPublicationTest {
    private fun emptyPage() = HistoryPaging.decode("""{"schema_version":1,"wallet_id":"fixture",
        "revision":"new-empty-revision","total_count":0,"matching_count":0,"pending_count":0,
        "offset":0,"last_scanned_height":100000,"chain_height":100000,"chain_time":1,"transfers":[]}""", "fixture")

    @Test fun successfulEmptyReorgPublishesOneConsistentSnapshotAndInvalidatesOldPolls() {
        val oldPage = HistoryPaging.decode("""{"schema_version":1,"wallet_id":"fixture","revision":"old",
            "total_count":1,"matching_count":1,"pending_count":0,"offset":0,
            "last_scanned_height":99990,"chain_height":99990,"chain_time":1,
            "transfers":[{"txid":"orphaned-receive","direction":"in","amount":835000000,
                "confirmations":1,"is_pending":false}]}""", "fixture")
        for (restore in listOf(0L, 99000L)) {
            val previous = WalletManager.UiState(walletId = "fixture", walletAddress = "fixture-address",
                refreshInProgress = true, balance = WalletCore.Balance(835000000, 835000000),
                transfers = oldPage.transfers, totalHistoryCount = 1, historyRevision = "old",
                balanceIsStaleWhileSyncing = true)
            val complete = WalletSnapshotPublication.completed(previous,
                WalletCore.SyncStatus(100000, 1, 1, 100000, restore), emptyPage(),
                WalletCore.Balance(0, 0), 123)
            assertTrue(complete.transfers.isEmpty())
            assertEquals(0, complete.totalHistoryCount)
            assertEquals(0, complete.pendingHistoryCount)
            assertEquals("new-empty-revision", complete.historyRevision)
            assertEquals(WalletCore.Balance(0, 0), complete.balance)
            assertFalse(complete.refreshInProgress)
            assertFalse(complete.balanceIsStaleWhileSyncing)
            assertEquals(123L, complete.lastBalanceRefreshAtMs)
            assertEquals(complete.lastBalanceRefreshAtMs, complete.lastTransfersRefreshAtMs)
            assertFalse(WalletSnapshotPublication.sameReadSession(previous, complete))
            assertTrue(WalletSnapshotPublication.sameReadSession(complete, complete))
        }
    }

    @Test fun incompleteMismatchedAndReplacedWalletSnapshotsCannotBePublished() {
        val previous = WalletManager.UiState(walletId = "fixture")
        val status = WalletCore.SyncStatus(100000, 1, 1, 100000, 0)
        for ((state, st, page) in listOf(
            Triple(previous, status.copy(lastScanned = 99999), emptyPage()),
            Triple(previous, status.copy(chainHeight = 0), emptyPage()),
            Triple(previous, status, emptyPage().copy(lastScannedHeight = 99999)),
            Triple(previous.copy(walletId = "replacement"), status, emptyPage()),
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                WalletSnapshotPublication.completed(state, st, page, WalletCore.Balance(0, 0), 123)
            }
        }
        assertFalse(WalletSnapshotPublication.sameReadSession(previous,
            previous.copy(historySession = "replacement-session")))
    }
}
