package com.nexatrode.nexawal.logic

/** target_height is commonly zero once a daemon is synced; that is not its chain height. */
object RestoreHeightPolicy {
    fun suggestedCreateHeight(height: Long, targetHeight: Long): Long {
        require(height >= 0 && targetHeight >= 0) { "Invalid daemon height" }
        val tip = if (targetHeight > 0) targetHeight else height
        require(tip > 0) { "Daemon did not report a usable height" }
        return (tip - 10).coerceAtLeast(0)
    }
}
