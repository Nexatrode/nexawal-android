package com.nexatrode.nexawal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.nexatrode.nexawal.logic.FiatRate
import com.nexatrode.nexawal.logic.FiatEstimate
import java.math.BigDecimal
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FiatPriceServiceTest {
    @Test
    fun cachedQuoteRoundTripPreservesTheServerDeadline() {
        val context = RuntimeEnvironment.getApplication()
        val rate = FiatRate("USD", BigDecimal("100"), 1_000_000L, "nexatrode", 1_001_000L)
        MoneroConfig.setCachedFiatRate(context, rate)
        val restored = MoneroConfig.cachedFiatRate(context)
        assertEquals(rate, restored)
        assertNull(FiatEstimate.liveRate(restored, 1_001_000L))
        MoneroConfig.setCachedFiatRate(context, null)
        assertNull(MoneroConfig.cachedFiatRate(context))
    }

    @Test
    fun legacyCacheWithoutDeadlineMustRefetch() {
        val context = RuntimeEnvironment.getApplication()
        MoneroConfig.setCachedFiatRate(context,
            FiatRate("USD", BigDecimal("100"), System.currentTimeMillis(), "nexatrode"))
        assertNull(MoneroConfig.cachedFiatRate(context))
    }

    @Test
    fun enabledRatesAreIndependentOfWalletI2pMode() {
        val context = RuntimeEnvironment.getApplication()
        MoneroConfig.setNetworkPolicy(context, MoneroConfig.NetworkPolicy.I2P)
        MoneroConfig.setFiatEstimatesEnabled(context, true)

        assertTrue(FiatPriceService(context).canFetch())
    }

    @Test
    fun disabledRatesStayDisabledInClearnetMode() {
        val context = RuntimeEnvironment.getApplication()
        MoneroConfig.setNetworkPolicy(context, MoneroConfig.NetworkPolicy.CLEARNET)
        MoneroConfig.setFiatEstimatesEnabled(context, false)

        assertFalse(FiatPriceService(context).canFetch())
    }
}
