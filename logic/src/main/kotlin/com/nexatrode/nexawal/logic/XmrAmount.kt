package com.nexatrode.nexawal.logic

object XmrAmount {
    const val PICONERO_PER_XMR: Long = 1_000_000_000_000L

    /**
     * Parse a decimal XMR amount into piconero.
     * Returns null on empty, invalid, more than 12 decimals, or overflow.
     */
    fun parsePiconero(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val norm = trimmed.replace(',', '.')
        val parts = norm.split('.', limit = 2)
        val wholeStr = parts[0].ifEmpty { "0" }
        val fracRaw = if (parts.size > 1) parts[1] else ""
        if (wholeStr.any { !it.isDigit() }) return null
        if (fracRaw.any { !it.isDigit() }) return null
        if (fracRaw.length > 12) return null
        val whole = wholeStr.toLongOrNull() ?: return null
        val fracPadded = fracRaw.padEnd(12, '0')
        val frac = if (fracPadded.isEmpty()) 0L else fracPadded.toLongOrNull() ?: return null
        val scaled = runCatching { Math.multiplyExact(whole, PICONERO_PER_XMR) }.getOrNull() ?: return null
        return runCatching { Math.addExact(scaled, frac) }.getOrNull()
    }
}
