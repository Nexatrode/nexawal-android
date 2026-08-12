package com.nexatrode.nexawal.logic

/**
 * Pure send preflight / retry classification helpers (no Android / JNI deps).
 */
object SendSafety {

    /** Overflow-safe check that amount + fee fits in unlocked balance. */
    fun hasUnlockedForExactSend(amountPiconero: Long, feePiconero: Long, unlockedPiconero: Long): Boolean {
        if (amountPiconero < 0L || feePiconero < 0L || unlockedPiconero < 0L) return false
        if (amountPiconero > unlockedPiconero) return false
        return feePiconero <= unlockedPiconero - amountPiconero
    }

    fun isFeeRateFailure(text: String): Boolean {
        val normalized = text.lowercase()
        return normalized.contains("fee_rate failed") || normalized.contains("fee_rate_failed")
    }

    /** Errors that imply construction/broadcast may have progressed past fee estimation. */
    fun looksLikePostBroadcastOrSpendFailure(text: String): Boolean {
        val normalized = text.lowercase()
        val markers = listOf(
            "key image",
            "already spent",
            "double spend",
            "txid",
            "transaction was rejected",
            "failed to broadcast",
            "relay",
            "daemon rejected",
        )
        return markers.any { normalized.contains(it) }
    }

    /**
     * Historical sibling-daemon fee fallback. Always null: the default public node is a
     * single endpoint (`https://rpc.nexatrode.com`) with no alternate host/port remap.
     */
    @Suppress("UNUSED_PARAMETER")
    fun siblingMonerodUrlIfNeeded(endpoint: String): String? = null

    /**
     * Only retry on fee_rate failures that clearly happened before spend/broadcast signals.
     */
    fun shouldRetryViaSiblingMonerod(
        errorText: String,
        coreMessage: String,
        endpoint: String,
    ): String? {
        val fallback = siblingMonerodUrlIfNeeded(endpoint) ?: return null
        val combined = "$errorText\n$coreMessage"
        if (looksLikePostBroadcastOrSpendFailure(combined)) return null
        if (isFeeRateFailure(errorText) || isFeeRateFailure(coreMessage)) return fallback
        return null
    }
}
