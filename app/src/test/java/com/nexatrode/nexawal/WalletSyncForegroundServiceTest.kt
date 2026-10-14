package com.nexatrode.nexawal

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletSyncForegroundServiceTest {
    @Test fun timeoutStopsTheServiceWithoutWaitingForNetworkOrNativeCleanup() {
        val controller = Robolectric.buildService(WalletSyncForegroundService::class.java).create()
        val service = controller.get()
        service.onTimeout(1, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }
}
