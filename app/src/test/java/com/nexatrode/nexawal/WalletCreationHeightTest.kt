package com.nexatrode.nexawal

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WalletCreationHeightTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun configuredNodeAndRealJsonUseHeightWhenSyncedTargetIsZero() = runBlocking {
        val manager = WalletManager(context)
        MoneroConfig.setNetworkPolicy(context, MoneroConfig.NetworkPolicy.CLEARNET)
        try {
            for (body in listOf(
                """{"height":3778000,"target_height":0}""",
                """{"result":{"height":3778000,"target_height":0}}""",
            )) {
                withLocalInfo(body) { endpoint ->
                    manager.setNodeUrl(endpoint)
                    assertEquals(3777990L, manager.suggestedCreateRestoreHeight())
                }
            }
        } finally { manager.close() }
    }

    @Test fun oversizedInfoDoesNotProduceAGenesisSuggestion() = runBlocking {
        val manager = WalletManager(context)
        MoneroConfig.setNetworkPolicy(context, MoneroConfig.NetworkPolicy.CLEARNET)
        try {
            withLocalInfo("", advertisedLength = 65537) { endpoint ->
                manager.setNodeUrl(endpoint)
                try { manager.suggestedCreateRestoreHeight(); fail("oversized node response accepted") }
                catch (error: IllegalArgumentException) { assertTrue(error.message!!.contains("too large")) }
            }
        } finally { manager.close() }
    }

    @Test fun invalidConfiguredProxyDoesNotFallThroughToClearnet() = runBlocking {
        val manager = WalletManager(context)
        MoneroConfig.setNetworkPolicy(context, MoneroConfig.NetworkPolicy.I2P)
        MoneroConfig.setI2pHttpProxyAddress(context, "not-a-host-port")
        try {
            try { manager.suggestedCreateRestoreHeight(); fail("invalid proxy silently bypassed") }
            catch (error: IllegalStateException) { assertTrue(error.message!!.contains("proxy is invalid")) }
        } finally {
            MoneroConfig.setNetworkPolicy(context, MoneroConfig.NetworkPolicy.CLEARNET)
            MoneroConfig.setI2pHttpProxyAddress(context, null)
            manager.close()
        }
    }

    private suspend fun withLocalInfo(body: String, advertisedLength: Int = body.toByteArray().size,
        operation: suspend (String) -> Unit) {
        ServerSocket(0).use { listener ->
            listener.soTimeout = 5000
            val executor = Executors.newSingleThreadExecutor()
            val request = executor.submit {
                listener.accept().use { socket ->
                    socket.soTimeout = 2000
                    val reader = socket.getInputStream().bufferedReader()
                    assertEquals("GET /get_info HTTP/1.1", reader.readLine())
                    while (!reader.readLine().isNullOrEmpty()) { /* Drain headers. */ }
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                        "Content-Length: $advertisedLength\r\nConnection: close\r\n\r\n$body").toByteArray())
                }
            }
            try {
                operation("http://127.0.0.1:${listener.localPort}")
                request.get(5, TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }
}
