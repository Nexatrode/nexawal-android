package com.nexatrode.nexawal

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WalletDiagnosticsTest {
    @Test fun ordinaryBuildDoesNotEmitSensitiveMessagesOrExceptions() {
        assumeFalse(BuildConfig.DEBUG && System.getenv("NEXAWAL_DIAGNOSTICS") == "1")
        ShadowLog.clear()
        val privateError = IllegalStateException("test-only private wallet data")
        WalletDiagnostics.i("wallet-security-test", "test-only private amount")
        WalletDiagnostics.w("wallet-security-test", "test-only private transaction", privateError)
        WalletDiagnostics.e("wallet-security-test", "test-only private endpoint", privateError)
        WalletDiagnostics.d("wallet-security-test", "test-only private balance")
        assertTrue(ShadowLog.getLogsForTag("wallet-security-test").isEmpty())
    }
}
