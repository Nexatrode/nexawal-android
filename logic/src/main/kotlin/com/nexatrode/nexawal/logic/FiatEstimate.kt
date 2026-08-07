package com.nexatrode.nexawal.logic

import java.math.BigDecimal
import java.math.RoundingMode

data class FiatRate(
    val currency: String,
    val fiatPerXmr: BigDecimal,
    val fetchedAtMs: Long,
    val source: String,
) {
    constructor(currency: String, fiatPerXmr: String, fetchedAtMs: Long, source: String) : this(
        currency = currency.uppercase(),
        fiatPerXmr = BigDecimal(fiatPerXmr),
        fetchedAtMs = fetchedAtMs,
        source = source,
    )
}

object FiatEstimate {
    const val MAX_AGE_MS: Long = 30L * 60L * 1_000L
    const val REFRESH_INTERVAL_MS: Long = 15L * 60L * 1_000L

    val supportedCurrencies: List<String> = listOf(
        "USD", "EUR", "GBP", "JPY", "CNY", "AUD", "CAD", "CHF", "HKD", "SGD",
        "NZD", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "RON", "TRY", "BRL",
        "MXN", "INR", "KRW", "IDR", "THB", "PHP", "MYR", "ZAR", "ILS", "ISK",
    )

    val currencyNames: Map<String, String> = mapOf(
        "USD" to "US Dollar",
        "EUR" to "Euro",
        "GBP" to "British Pound",
        "JPY" to "Japanese Yen",
        "CNY" to "Chinese Yuan",
        "AUD" to "Australian Dollar",
        "CAD" to "Canadian Dollar",
        "CHF" to "Swiss Franc",
        "HKD" to "Hong Kong Dollar",
        "SGD" to "Singapore Dollar",
        "NZD" to "New Zealand Dollar",
        "SEK" to "Swedish Krona",
        "NOK" to "Norwegian Krone",
        "DKK" to "Danish Krone",
        "PLN" to "Polish Zloty",
        "CZK" to "Czech Koruna",
        "HUF" to "Hungarian Forint",
        "RON" to "Romanian Leu",
        "TRY" to "Turkish Lira",
        "BRL" to "Brazilian Real",
        "MXN" to "Mexican Peso",
        "INR" to "Indian Rupee",
        "KRW" to "South Korean Won",
        "IDR" to "Indonesian Rupiah",
        "THB" to "Thai Baht",
        "PHP" to "Philippine Peso",
        "MYR" to "Malaysian Ringgit",
        "ZAR" to "South African Rand",
        "ILS" to "Israeli Shekel",
        "ISK" to "Icelandic Krona",
    )

    private val zeroDecimal = setOf("JPY", "KRW", "HUF", "ISK")
    private val symbols = mapOf(
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£",
        "JPY" to "¥",
        "CNY" to "¥",
        "KRW" to "₩",
        "INR" to "₹",
        "AUD" to "A$",
        "CAD" to "C$",
        "HKD" to "HK$",
        "SGD" to "S$",
        "NZD" to "NZ$",
        "BRL" to "R$",
        "MXN" to "MX$",
    )
    private val piconeroPerXmr = BigDecimal("1000000000000")

    fun isSupported(code: String): Boolean = supportedCurrencies.contains(code.uppercase())

    fun hintedCurrency(localeCurrencyCode: String?): String {
        val code = localeCurrencyCode?.trim()?.uppercase().orEmpty()
        return if (code.isNotEmpty() && isSupported(code)) code else "USD"
    }

    fun decimalPlaces(currency: String): Int = if (zeroDecimal.contains(currency.uppercase())) 0 else 2

    fun isFresh(fetchedAtMs: Long, nowMs: Long, maxAgeMs: Long = MAX_AGE_MS): Boolean {
        return nowMs >= fetchedAtMs && (nowMs - fetchedAtMs) < maxAgeMs
    }

    fun liveRate(rate: FiatRate?, nowMs: Long): FiatRate? {
        return rate?.takeIf { isFresh(it.fetchedAtMs, nowMs) }
    }

    /** Remaining ms until a live rate must be hidden. `0` means hide now. */
    fun msUntilStale(fetchedAtMs: Long, nowMs: Long, maxAgeMs: Long = MAX_AGE_MS): Long {
        val remaining = fetchedAtMs + maxAgeMs - nowMs
        return if (remaining > 0) remaining else 0L
    }

    /**
     * First-seen snapshots are only for transfers timed at/after opt-in.
     * Missing or zero timestamps are skipped; send/sweep still records explicitly.
     */
    fun shouldRecordSeenSnapshot(txTimestampSeconds: Long?, optedInAtMs: Long): Boolean {
        if (optedInAtMs <= 0L) return false
        val ts = txTimestampSeconds ?: return false
        if (ts <= 0L) return false
        if (ts > Long.MAX_VALUE / 1000L) return optedInAtMs <= Long.MAX_VALUE
        return ts * 1000L >= optedInAtMs
    }

    fun parseKrakenLastTrade(json: String): BigDecimal? {
        val resultIdx = json.indexOf("\"result\"")
        if (resultIdx < 0) return null
        val cIdx = json.indexOf("\"c\"", resultIdx)
        if (cIdx < 0) return null
        val bracket = json.indexOf('[', cIdx)
        if (bracket < 0) return null
        val quote1 = json.indexOf('"', bracket + 1)
        if (quote1 < 0) return null
        val quote2 = json.indexOf('"', quote1 + 1)
        if (quote2 < 0) return null
        return decimalOrNull(json.substring(quote1 + 1, quote2))
    }

    fun parseFrankfurterRate(json: String, symbol: String): BigDecimal? {
        val code = symbol.uppercase()
        if (code == "USD") return BigDecimal.ONE
        val ratesIdx = json.indexOf("\"rates\"")
        if (ratesIdx < 0) return null
        val key = "\"$code\""
        val keyIdx = json.indexOf(key, ratesIdx)
        if (keyIdx < 0) return null
        val colon = json.indexOf(':', keyIdx + key.length)
        if (colon < 0) return null
        var start = colon + 1
        while (start < json.length && json[start].isWhitespace()) start++
        var end = start
        while (end < json.length) {
            val ch = json[end]
            if (ch == ',' || ch == '}' || ch.isWhitespace()) break
            end++
        }
        return decimalOrNull(json.substring(start, end))
    }

    fun combine(usdPerXmr: BigDecimal, usdToFiat: BigDecimal): BigDecimal = usdPerXmr.multiply(usdToFiat)

    fun fiatAmount(piconero: Long, fiatPerXmr: BigDecimal): BigDecimal {
        return BigDecimal.valueOf(piconero).divide(piconeroPerXmr, 18, RoundingMode.DOWN).multiply(fiatPerXmr)
    }

    fun symbol(currency: String): String? = symbols[currency.uppercase()]

    /**
     * Convert a typed fiat amount to piconero using the live rate.
     * Rounds **down** so send never exceeds the typed fiat value.
     */
    fun piconeroFromFiat(fiatText: String, rate: FiatRate): Long? {
        if (rate.fiatPerXmr.signum() <= 0) return null
        val fiat = decimalOrNull(fiatText.replace(',', '.')) ?: return null
        if (fiat.signum() < 0) return null
        if (fiat.signum() == 0) return 0L
        val xmr = fiat.divide(rate.fiatPerXmr, 18, RoundingMode.DOWN)
        val pico = xmr.multiply(piconeroPerXmr).setScale(0, RoundingMode.DOWN)
        return runCatching { pico.longValueExact() }.getOrNull()
    }

    /** Fiat amount string for the input field (no ≈ prefix). */
    fun formatFiatForInput(piconero: Long, rate: FiatRate): String {
        val places = decimalPlaces(rate.currency)
        val amount = fiatAmount(piconero, rate.fiatPerXmr).setScale(places, RoundingMode.HALF_UP)
        return formatPlainNumber(amount, places)
    }

    fun formatXmrForInput(piconero: Long): String = XmrAmount.formatForInput(piconero)

    /** Secondary line when the user is typing fiat: `≈ 0.123456 XMR`. */
    fun formatXmrApprox(piconero: Long): String = "≈ ${formatXmrForInput(piconero)} XMR"

    fun formatApprox(amount: BigDecimal, currency: String): String {
        val code = currency.uppercase()
        val places = decimalPlaces(code)
        val number = formatNumber(amount.setScale(places, RoundingMode.HALF_UP), places)
        val symbol = symbols[code]
        return if (symbol != null) "≈ $symbol$number" else "≈ $number $code"
    }

    fun formatApprox(piconero: Long, rate: FiatRate): String {
        return formatApprox(fiatAmount(piconero, rate.fiatPerXmr), rate.currency)
    }

    fun liveApproxText(piconero: Long, rate: FiatRate?, nowMs: Long): String? {
        val live = liveRate(rate, nowMs) ?: return null
        return formatApprox(piconero, live)
    }

    fun recordedApproxText(piconero: Long, fiatPerXmr: BigDecimal, currency: String): String {
        return formatApprox(fiatAmount(piconero, fiatPerXmr), currency)
    }

    fun decimalOrNull(raw: String): BigDecimal? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { BigDecimal(trimmed) }.getOrNull()
    }

    private fun formatPlainNumber(value: BigDecimal, decimals: Int): String {
        val negative = value.signum() < 0
        val abs = value.abs()
        val plain = abs.toPlainString()
        val parts = plain.split('.', limit = 2)
        val whole = parts[0].filter { it.isDigit() }.ifEmpty { "0" }
        if (decimals == 0) {
            return if (negative) "-$whole" else whole
        }
        var frac = if (parts.size > 1) parts[1].filter { it.isDigit() } else ""
        if (frac.length > decimals) frac = frac.substring(0, decimals)
        while (frac.length < decimals) frac += "0"
        while (frac.length > 1 && frac.endsWith('0')) {
            frac = frac.dropLast(1)
        }
        if (frac.all { it == '0' }) {
            return if (negative) "-$whole" else whole
        }
        return "${if (negative) "-" else ""}$whole.$frac"
    }

    private fun formatNumber(value: BigDecimal, decimals: Int): String {
        val negative = value.signum() < 0
        val abs = value.abs()
        val plain = abs.toPlainString()
        val parts = plain.split('.', limit = 2)
        val wholeDigits = parts[0].filter { it.isDigit() }.ifEmpty { "0" }
        val groupedWhole = groupThousands(wholeDigits)
        if (decimals == 0) {
            return if (negative) "-$groupedWhole" else groupedWhole
        }
        var frac = if (parts.size > 1) parts[1].filter { it.isDigit() } else ""
        if (frac.length > decimals) frac = frac.substring(0, decimals)
        while (frac.length < decimals) frac += "0"
        return "${if (negative) "-" else ""}$groupedWhole.$frac"
    }

    private fun groupThousands(digits: String): String {
        val out = StringBuilder()
        digits.reversed().forEachIndexed { index, ch ->
            if (index > 0 && index % 3 == 0) out.append(',')
            out.append(ch)
        }
        return out.reverse().toString()
    }
}
