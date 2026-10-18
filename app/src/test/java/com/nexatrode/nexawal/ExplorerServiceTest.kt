package com.nexatrode.nexawal

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.async
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExplorerServiceTest {
    private val id = "a".repeat(64)
    private fun body(edits: Map<String, Any> = emptyMap()): String {
        val json = JSONObject("""{"api_version":1,"network":"mainnet","txid":"$id","status":"confirmed","fee_piconero":"18446744073709551615","block_height":8,"block_hash":"$id","block_timestamp":900,"confirmations":2,"chain_height":10,"tip_hash":"$id","node_ready":true,"observed_at":1000}""")
        edits.forEach { (key, value) -> json.put(key, value) }
        return json.toString()
    }
    @Test fun startsDisabledAndRemainsSeparateFromRatesAndI2p() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        ExplorerAccess.preferences(context).edit().clear().commit()
        assertFalse(ExplorerAccess.enabled(context))
        MoneroConfig.setFiatEstimatesEnabled(context, true)
        MoneroConfig.setNetworkPolicy(context, MoneroConfig.NetworkPolicy.I2P)
        assertFalse(ExplorerAccess.enabled(context))
        // The consent gate cancels before enqueueing an HTTP request.
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun callStart(call: Call) { requests.incrementAndGet() }
        }).build()
        ExplorerService(context, client).use { service -> assertTrue(runCatching { service.fetch(id) }.isFailure) }
        assertEquals(0, requests.get())
        ExplorerAccess.setEnabled(context, true)
        assertTrue(ExplorerAccess.enabled(context))
        ExplorerAccess.setEnabled(context, false)
    }
    @Test fun validatesIdentityAndExactPublicData() {
        val result = ExplorerObservation.parse(body(mapOf("node_ready" to false)), id, 1000)
        assertEquals("18446744073709551615", result.feePiconero)
        assertFalse(result.nodeReady)
        assertEquals(2L, result.confirmations)
        for (edit in listOf(
            mapOf("network" to "stagenet"), mapOf("api_version" to 2), mapOf("txid" to "b".repeat(64)),
            mapOf("confirmations" to 1), mapOf("block_height" to 10), mapOf("fee_piconero" to "18446744073709551616"),
            mapOf("fee_piconero" to 1.5), mapOf("observed_at" to 699), mapOf("observed_at" to 1121),
            mapOf("node_ready" to "true"), mapOf("confirmations" to 2.5),
        )) assertTrue(edit.toString(), runCatching { ExplorerObservation.parse(body(edit), id, 1000) }.isFailure)
    }
    @Test fun pendingNullFeeAndLimits() {
        val value = ExplorerObservation.parse(body(mapOf("status" to "pending", "fee_piconero" to JSONObject.NULL, "block_height" to JSONObject.NULL, "block_hash" to JSONObject.NULL, "block_timestamp" to JSONObject.NULL, "confirmations" to 0)), id, 1000)
        assertNull(value.feePiconero)
        assertNull(value.height)
        assertTrue(runCatching { ExplorerObservation.parse(" ".repeat(16_385), id, 1000) }.isFailure)
        assertFalse(ExplorerObservation.validId("../$id"))
    }
    @Test fun enabledLookupUsesOnlyOneIdAndRejectsHtml404OrOversizedBody() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        ExplorerAccess.setEnabled(context, true)
        val count = AtomicInteger()
        for ((status, type, text) in listOf(
            Triple(200, "application/json", body(mapOf("observed_at" to System.currentTimeMillis() / 1000))),
            Triple(404, "text/html", "not an API"), Triple(200, "application/json", " ".repeat(16_385)),
        )) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                count.incrementAndGet()
                val request = chain.request()
                assertEquals("https", request.url.scheme)
                assertEquals("explorer.nexawal.com", request.url.host)
                assertEquals("/api/v1/transactions/$id", request.url.encodedPath)
                assertNull(request.url.query)
                assertNull(request.body)
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                    .header("Content-Type", type).body(text.toResponseBody(type.toMediaType())).build()
            }.build()
            ExplorerService(context, client).use { service ->
                val result = runCatching { service.fetch(id) }
                assertEquals(status == 200 && text.length < 16_385, result.isSuccess)
                if (status == 404) assertFalse(result.exceptionOrNull() is ExplorerMissing)
            }
        }
        assertEquals(3, count.get())
        ExplorerAccess.setEnabled(context, false)
    }
    @Test fun disablingDiscardsAResponseAlreadyInFlight() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        ExplorerAccess.setEnabled(context, true)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .header("Content-Type", "application/json")
                .body(body(mapOf("observed_at" to System.currentTimeMillis() / 1000)).toResponseBody("application/json".toMediaType())).build()
        }.build()
        ExplorerService(context, client).use { service ->
            val response = async { runCatching { service.fetch(id) } }
            kotlinx.coroutines.yield()
            assertTrue(started.await(3, TimeUnit.SECONDS))
            ExplorerAccess.setEnabled(context, false)
            release.countDown()
            assertTrue(response.await().isFailure)
        }
    }
}
