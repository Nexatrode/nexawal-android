package com.nexatrode.nexawal.ui

/**
 * True when the current sync/refresh state should be presented as a "stalled" error.
 *
 * Prefers the typed [syncStalled] flag (set by [com.nexatrode.nexawal.WalletManager] when a
 * refresh fails with `RefreshStalledException`), falling back to English substring matching on
 * [errorMessage] for old in-flight/cached errors that predate the typed flag.
 */
fun isSyncStallError(syncStalled: Boolean, errorMessage: String?): Boolean =
    syncStalled || (errorMessage?.contains("Refresh stalled", ignoreCase = true) == true)
