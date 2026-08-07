package com.nexatrode.nexawal.ui

/**
 * True when the current sync/refresh state should be presented as a "stalled" error.
 *
 * Driven only by the typed [syncStalled] flag set by [com.nexatrode.nexawal.WalletManager]
 * when a refresh fails with [com.nexatrode.nexawal.RefreshStalledException].
 */
fun isSyncStallError(syncStalled: Boolean): Boolean = syncStalled
