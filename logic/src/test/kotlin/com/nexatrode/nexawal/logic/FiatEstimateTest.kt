package com.nexatrode.nexawal.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FiatEstimateTest {
    private val krakenUsd = """
        {"error":[],"result":{"XXMRZUSD":{"a":["356.73000000","9","9.000"],"b":["356.61000000","1","1.000"],"c":["356.85000000","0.02761000"],"v":["4487.01313992","6468.48583715"]}}}
    """.trimIndent()
    private val krakenEur = """
        {"error":[],"result":{"XXMRZEUR":{"c":["308.84000000","0.06719369"]}}}
    """.trimIndent()
    private val frankfurter = """
        {"amount":1.0,"base":"USD","date":"2026-08-05","rates":{"BRL":5.1153,"CAD":1.4047,"EUR":0.8655,"GBP":0.74191,"JPY":157.59}}
    """.trimIndent()

    @Test
    fun parseKrakenLastTrade() {
        assertEquals(BigDecimal("356.85000000"), FiatEstimate.parseKrakenLastTrade(krakenUsd))
        assertEquals(BigDecimal("308.84000000"), FiatEstimate.parseKrakenLastTrade(krakenEur))
        assertNull(FiatEstimate.parseKrakenLastTrade("{}"))
    }

    @Test
    fun parseFrankfurterAndCombine() {
        assertEquals(BigDecimal("0.74191"), FiatEstimate.parseFrankfurterRate(frankfurter, "GBP"))
        assertEquals(BigDecimal("157.59"), FiatEstimate.parseFrankfurterRate(frankfurter, "JPY"))
        assertEquals(BigDecimal.ONE, FiatEstimate.parseFrankfurterRate(frankfurter, "USD"))
        assertNull(FiatEstimate.parseFrankfurterRate(frankfurter, "UAH"))

        val usd = FiatEstimate.parseKrakenLastTrade(krakenUsd)!!
        val gbpFx = FiatEstimate.parseFrankfurterRate(frankfurter, "GBP")!!
        assertEquals(0, usd.multiply(gbpFx).compareTo(FiatEstimate.combine(usd, gbpFx)))
    }

    @Test
    fun freshnessBoundary() {
        val fetched = 1_000_000L
        assertTrue(FiatEstimate.isFresh(fetched, fetched + FiatEstimate.MAX_AGE_MS - 1))
        assertFalse(FiatEstimate.isFresh(fetched, fetched + FiatEstimate.MAX_AGE_MS))
        assertFalse(FiatEstimate.isFresh(fetched, fetched - 1))
    }

    @Test
    fun conversionAndFormatting() {
        val usdRate = FiatRate("USD", BigDecimal("356.85"), 10L, "kraken")
        assertEquals(0, BigDecimal("356.85").compareTo(FiatEstimate.fiatAmount(1_000_000_000_000L, usdRate.fiatPerXmr)))
        assertEquals("≈ $356.85", FiatEstimate.formatApprox(1_000_000_000_000L, usdRate))
        assertEquals("≈ $356.85", FiatEstimate.liveApproxText(1_000_000_000_000L, usdRate, 10L + 60_000L))
        assertNull(FiatEstimate.liveApproxText(1_000_000_000_000L, usdRate, 10L + FiatEstimate.MAX_AGE_MS))

        val dust = FiatEstimate.fiatAmount(1L, usdRate.fiatPerXmr)
        assertEquals("≈ $0.00", FiatEstimate.formatApprox(dust, "USD"))

        val jpyRate = FiatRate("JPY", BigDecimal("56234.4"), 10L, "kraken+frankfurter")
        assertEquals("≈ ¥56,234", FiatEstimate.formatApprox(1_000_000_000_000L, jpyRate))
        assertEquals(
            "≈ $356.85",
            FiatEstimate.recordedApproxText(1_000_000_000_000L, usdRate.fiatPerXmr, "USD"),
        )
    }

    @Test
    fun piconeroFromFiatRoundsDown() {
        val usdRate = FiatRate("USD", BigDecimal("100"), 10L, "test")
        assertEquals(500_000_000_000L, FiatEstimate.piconeroFromFiat("50", usdRate))
        assertEquals(500_000_000_000L, FiatEstimate.piconeroFromFiat("50.000000000001", usdRate))
        assertEquals(0L, FiatEstimate.piconeroFromFiat("0", usdRate))
        assertNull(FiatEstimate.piconeroFromFiat("", usdRate))
        assertNull(FiatEstimate.piconeroFromFiat("abc", usdRate))

        assertEquals("50", FiatEstimate.formatFiatForInput(500_000_000_000L, usdRate))
        assertEquals("0.5", FiatEstimate.formatXmrForInput(500_000_000_000L))
        assertEquals("≈ 0.5 XMR", FiatEstimate.formatXmrApprox(500_000_000_000L))
        assertEquals("$", FiatEstimate.symbol("USD"))
    }

    @Test
    fun localeHint() {
        assertEquals("EUR", FiatEstimate.hintedCurrency("eur"))
        assertEquals("USD", FiatEstimate.hintedCurrency("UAH"))
        assertEquals("USD", FiatEstimate.hintedCurrency(null))
    }

    @Test
    fun seenSnapshotSkipsHistoryBeforeOptIn() {
        val optedIn = 1_700_000_000_000L
        assertFalse(FiatEstimate.shouldRecordSeenSnapshot(null, optedIn))
        assertFalse(FiatEstimate.shouldRecordSeenSnapshot(0L, optedIn))
        assertFalse(FiatEstimate.shouldRecordSeenSnapshot(1_699_999_999L, optedIn))
        assertTrue(FiatEstimate.shouldRecordSeenSnapshot(1_700_000_000L, optedIn))
        assertTrue(FiatEstimate.shouldRecordSeenSnapshot(1_700_000_001L, optedIn))
        assertFalse(FiatEstimate.shouldRecordSeenSnapshot(1_800_000_000L, 0L))
        assertEquals(5L, FiatEstimate.msUntilStale(10L, 10L + FiatEstimate.MAX_AGE_MS - 5L))
        assertEquals(0L, FiatEstimate.msUntilStale(10L, 10L + FiatEstimate.MAX_AGE_MS))
    }
}
