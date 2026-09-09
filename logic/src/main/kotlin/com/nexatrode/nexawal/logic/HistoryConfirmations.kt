package com.nexatrode.nexawal.logic

/** Tip-only changes update presentation without invalidating ledger pages. */
object HistoryConfirmations {
    fun count(height: Long?, pending: Boolean, chainHeight: Long, cached: Long): Long {
        if (pending) return 0
        if (height == null) return cached
        if (height <= 0) return 0
        if (chainHeight <= 0) return cached
        // Match WalletCore's saturating subtraction followed by +1.
        return (if (chainHeight >= height) chainHeight - height else 0) + 1
    }
}
