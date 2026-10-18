package com.nexatrode.nexawal

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WalletNodeMigrationTest {
    @Test fun previousBuiltinRPCMigratesButCustomNodesRemainConfigured() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val settings = File(context.filesDir, "WalletSlot/settings.json")
        settings.parentFile!!.mkdirs()
        val previous = settings.takeIf { it.exists() }?.readBytes()
        try {
            for ((saved, expected) in listOf(
                "https://rpc.nexatrode.com" to "https://rpc.nexawal.com",
                " HTTPS://RPC.NEXATRODE.COM/ " to "https://rpc.nexawal.com",
                "https://rpc.nexatrode.com:443/" to "https://rpc.nexawal.com",
                "https://monero.nexatrode.com" to "https://monero.nexatrode.com",
                "http://127.0.0.1:18081" to "http://127.0.0.1:18081",
                "http://example.b32.i2p" to "http://example.b32.i2p",
                "https://rpc.nexatrode.com/custom" to "https://rpc.nexatrode.com/custom",
            )) {
                settings.writeText(JSONObject().put("nodeUrl", saved).put("savedAtMs", 0).toString())
                val manager = WalletManager(context)
                try {
                    assertTrue(manager.loadSettingsOnLaunch())
                    assertEquals(expected, manager.currentNodeUrl())
                } finally { manager.close() }
            }
        } finally {
            if (previous != null) settings.writeBytes(previous) else settings.delete()
        }
    }
}
