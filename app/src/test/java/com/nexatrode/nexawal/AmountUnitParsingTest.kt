package com.nexatrode.nexawal

import com.nexatrode.nexawal.ui.AmountInputMode
import com.nexatrode.nexawal.ui.AmountUnitParsing
import com.nexatrode.nexawal.logic.FiatRate
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AmountUnitParsingTest {
    @Test
    fun expiryClearsFiatNumberBeforeChangingUnit() {
        var text = "50"
        var mode = AmountInputMode.FIAT
        AmountUnitParsing.clearExpiredFiatAmount(
            onTextChange = { text = it },
            onModeChange = { assertEquals("", text); mode = it },
        )
        assertEquals(AmountInputMode.XMR, mode)
        assertNull(AmountUnitParsing.piconero(text, mode, null))
    }

    @Test
    fun parserChecksExpiryEvenBeforeTheUiTimerFires() {
        val now = System.currentTimeMillis()
        val expired = FiatRate("USD", BigDecimal("100"), now - 1_000, "nexatrode", now)
        assertNull(AmountUnitParsing.piconero("50", AmountInputMode.FIAT, expired))
        assertNull(AmountUnitParsing.xmrAmountForUri("50", AmountInputMode.FIAT, expired))
        assertEquals(50_000_000_000_000L, AmountUnitParsing.piconero("50", AmountInputMode.XMR, expired))
    }
}
