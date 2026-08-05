package com.nexatrode.nexawal.logic

/**
 * Pure network-policy helpers shared by Settings / WalletManager (no Android Context).
 */
object NetworkRouting {

    enum class Policy {
        CLEARNET,
        I2P,
        HYBRID,
        ;

        companion object {
            fun fromRaw(raw: String?): Policy =
                when (raw?.lowercase()) {
                    "i2p" -> I2P
                    "hybrid" -> HYBRID
                    else -> CLEARNET
                }
        }
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        explicitNodeUrl(trimmed)?.let { return it }
        // I2P / legacy host:port values have no scheme. Default those to http.
        return if (trimmed.isEmpty()) trimmed else "http://$trimmed"
    }

    /** Clearnet node field: user must type http:// or https://. No scheme guessing. */
    fun explicitNodeUrl(raw: String): String? {
        val trimmed = raw.trim()
        val lower = trimmed.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return trimmed
        }
        return null
    }

    fun scanNodeUrl(policy: Policy, clearnetNodeUrl: String, i2pRpcAddress: String): String {
        return when (policy) {
            Policy.CLEARNET, Policy.HYBRID -> clearnetNodeUrl
            Policy.I2P -> normalizeUrl(i2pRpcAddress)
        }
    }

    fun broadcastNodeUrl(policy: Policy, clearnetNodeUrl: String, i2pRpcAddress: String): String {
        return when (policy) {
            Policy.CLEARNET -> clearnetNodeUrl
            Policy.I2P, Policy.HYBRID -> normalizeUrl(i2pRpcAddress)
        }
    }

    /** True when daemon RPC for this policy should go through the I2P HTTP proxy. */
    fun shouldUseI2pHttpProxy(
        policy: Policy,
        proxyConfigured: Boolean,
        forBroadcast: Boolean,
    ): Boolean {
        if (!proxyConfigured) return false
        return when (policy) {
            Policy.CLEARNET -> false
            Policy.I2P -> true
            Policy.HYBRID -> forBroadcast
        }
    }
}
