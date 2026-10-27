package com.nexatrode.nexawal.logic

/**
 * Pure network-policy helpers shared by Settings / WalletManager (no Android Context).
 */
object NetworkRouting {
    const val DEFAULT_I2P_RPC_ADDRESS = "7lfi73h743qm663hv4x5q6ccgc4jc5ncl65dfvpe665m6pawwdxa.b32.i2p"

    fun validateI2pConfiguration(nodeAddress: String, proxyAddress: String?) {
        val node = runCatching { java.net.URI(normalizeUrl(nodeAddress)) }.getOrNull()
        require(node != null && node.scheme.equals("http", ignoreCase = true) &&
            node.host?.lowercase()?.endsWith(".i2p") == true && node.query == null && node.fragment == null &&
            (node.port == -1 || node.port in 1..65535)) {
            "Enter an HTTP I2P RPC address ending in .i2p, with a valid port (not the P2P address)"
        }
        val raw = proxyAddress?.trim().orEmpty()
        val proxy = runCatching { java.net.URI(if (raw.contains("://")) raw else "http://$raw") }.getOrNull()
        require(proxy != null && proxy.scheme.equals("http", ignoreCase = true) &&
            !proxy.host.isNullOrEmpty() && proxy.port in 1..65535 && proxy.userInfo == null &&
            proxy.query == null && proxy.fragment == null && (proxy.path.isNullOrEmpty() || proxy.path == "/")) {
            "Enter an I2P HTTP proxy as host:port, usually 127.0.0.1:4444 (not SOCKS or HTTPS)"
        }
    }

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
