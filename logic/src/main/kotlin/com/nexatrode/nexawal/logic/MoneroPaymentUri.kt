package com.nexatrode.nexawal.logic

/**
 * Parsed `monero:` payment URI.
 *
 * `spend_key` / `view_key` query params are ignored and must never become send targets.
 */
data class MoneroPaymentUri(
    val address: String,
    val amountXmr: String?,
) {
    companion object {
        fun parse(raw: String): MoneroPaymentUri? {
            val trimmed = raw.trim()
            if (!trimmed.lowercase().startsWith("monero:")) return null

            var remainder = trimmed.substring("monero:".length)
            if (remainder.startsWith("//")) {
                remainder = remainder.drop(2)
            }

            val queryIndex = remainder.indexOf('?')
            val addressCandidate = if (queryIndex >= 0) remainder.substring(0, queryIndex) else remainder
            val queryString = if (queryIndex >= 0) remainder.substring(queryIndex + 1) else null

            val address = addressCandidate.trim('/').trim()
            if (address.isEmpty()) return null

            var amountXmr: String? = null
            if (!queryString.isNullOrEmpty()) {
                for (pair in queryString.split('&')) {
                    val eq = pair.indexOf('=')
                    val name = (if (eq >= 0) pair.substring(0, eq) else pair).lowercase()
                    val value = if (eq >= 0) {
                        decode(pair.substring(eq + 1))
                    } else {
                        ""
                    }
                    if (name == "spend_key" || name == "view_key" || name == "spendkey" || name == "viewkey") {
                        continue
                    }
                    if ((name == "amount" || name == "tx_amount") && value.isNotEmpty() && amountXmr == null) {
                        amountXmr = value
                    }
                }
            }

            return MoneroPaymentUri(address = address, amountXmr = amountXmr)
        }

        private fun decode(value: String): String =
            try {
                java.net.URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
            } catch (_: Exception) {
                value
            }
    }
}
