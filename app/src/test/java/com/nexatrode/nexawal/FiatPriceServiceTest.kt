package com.nexatrode.nexawal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FiatPriceServiceTest {
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
