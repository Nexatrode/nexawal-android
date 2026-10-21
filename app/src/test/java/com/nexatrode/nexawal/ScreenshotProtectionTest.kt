package com.nexatrode.nexawal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotProtectionTest {
    @Test
    fun protectsSeedSetupAndRecoveryScreens() {
        assertTrue(ScreenshotProtection.shouldProtect(seedSetupVisible = true, recoveryFlowVisible = false))
        assertTrue(ScreenshotProtection.shouldProtect(seedSetupVisible = false, recoveryFlowVisible = true))
        assertTrue(ScreenshotProtection.shouldProtect(seedSetupVisible = true, recoveryFlowVisible = true))
    }

    @Test
    fun allowsCaptureForOrdinaryWalletScreens() {
        assertFalse(ScreenshotProtection.shouldProtect(seedSetupVisible = false, recoveryFlowVisible = false))
    }
}
