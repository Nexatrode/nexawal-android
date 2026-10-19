package com.nexatrode.nexawal.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexatrode.nexawal.*
import com.nexatrode.nexawal.R
import com.nexatrode.nexawal.ui.theme.NexawalTheme
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.io.IOException

/** Real Compose effect + real service, but HTTP is intercepted before DNS/sockets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-night-xhdpi")
class ExplorerLifecycleTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val id = "a".repeat(64)
    private val blockHash = "d".repeat(64)
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private class Gate { val release = CountDownLatch(1) }
    private val gates = List(4) { Gate() }
    private val calls = AtomicInteger()
    private val cancelled = AtomicInteger()
    private val finished = AtomicInteger()
    private val services = mutableListOf<ExplorerService>()
    private val paths = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val visible = mutableStateOf(true)
    private val session = mutableStateOf("wallet-session-one")
    private lateinit var owner: Owner
    private lateinit var activeService: MutableState<ExplorerService>

    private fun service(): ExplorerService {
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { cancelled.incrementAndGet() }
        }).addInterceptor { chain ->
            val index = calls.getAndIncrement()
            paths.add(chain.request().url.encodedPath)
            try {
                if (!gates[index].release.await(30, TimeUnit.SECONDS)) throw IOException("synthetic response was never released")
                var body = """{"api_version":1,"network":"mainnet","txid":"$id","status":"confirmed","fee_piconero":"${44540000 + index}","block_height":8,"block_hash":"$blockHash","block_timestamp":900,"confirmations":2,"chain_height":10,"tip_hash":"${"c".repeat(64)}","node_ready":true,"double_spend_seen":true,"observed_at":${System.currentTimeMillis() / 1000}}"""
                val path = chain.request().url.encodedPath
                if (path.contains("/details/")) {
                    val json = org.json.JSONObject(body)
                    val fixture = org.json.JSONObject(javaClass.classLoader!!.getResourceAsStream("explorer_advanced_page0.json")!!.bufferedReader().use { it.readText() })
                    val details = fixture.getJSONObject("details")
                    if (path.endsWith("/1")) {
                        val input = details.getJSONArray("inputs").getJSONObject(0).put("index",10)
                        val output = details.getJSONArray("outputs").getJSONObject(0).put("index",10)
                        val output2 = details.getJSONArray("outputs").getJSONObject(1).put("index",11)
                        details.put("page",1).put("inputs",org.json.JSONArray().put(input)).put("outputs",org.json.JSONArray().put(output).put(output2))
                    }
                    body = json.put("details",details).toString()
                }
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("synthetic").header("Content-Type", "application/json")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            } finally { finished.incrementAndGet() }
        }.build()
        return ExplorerService(rule.activity, client).also { services.add(it) }
    }

    private fun mount(mainnet: Boolean = true, enabled: Boolean = true) {
        ExplorerAccess.setEnabled(rule.activity, enabled)
        activeService = mutableStateOf(service())
        rule.runOnIdle { owner = Owner(); owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.setContent {
            NexawalTheme(darkTheme = true) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    if (visible.value) ExplorerDetailsScreen(id, mainnet, activeService.value, session.value) { visible.value = false }
                }
            }
        }
    }

    private fun waitFor(predicate: () -> Boolean) {
        // In Robolectric, drain recomposition/disposal before waiting on an IO
        // thread counter; a wall-clock wait alone need not draw the next frame.
        rule.waitForIdle()
        rule.waitUntil(5_000, predicate)
    }
    private fun refresh() {
        rule.onNodeWithText(rule.activity.getString(R.string.explorer_refresh)).performScrollTo().performClick()
    }
    private fun assertNoResult() {
        rule.onNodeWithText(blockHash).assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.explorer_unavailable)).assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.explorer_missing)).assertDoesNotExist()
    }

    @After fun cleanup() {
        gates.forEach { it.release.countDown() }
        services.forEach { it.close() }
        ExplorerAccess.setEnabled(rule.activity, false)
    }

    @Test fun backgroundCancelsLateResponseAndResumeNeedsManualRefresh() {
        mount()
        waitFor { calls.get() == 1 }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        waitFor { cancelled.get() == 1 }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        gates[0].release.countDown()
        waitFor { finished.get() == 1 }
        rule.waitForIdle()
        assertNoResult()
        assertEquals(1, calls.get())
        refresh()
        waitFor { calls.get() == 2 }
        gates[1].release.countDown()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(blockHash).fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        rule.waitForIdle()
        assertNoResult()
    }

    @Test fun disableReenableClearsAndDoesNotRestartLookup() {
        mount()
        waitFor { calls.get() == 1 }
        rule.runOnIdle { ExplorerAccess.setEnabled(rule.activity, false) }
        waitFor { cancelled.get() == 1 }
        rule.runOnIdle { ExplorerAccess.setEnabled(rule.activity, true) }
        gates[0].release.countDown()
        waitFor { finished.get() == 1 }
        rule.waitForIdle()
        assertNoResult()
        assertEquals(1, calls.get())
        refresh()
        waitFor { calls.get() == 2 }
        gates[1].release.countDown()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(blockHash).fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle { ExplorerAccess.setEnabled(rule.activity, false) }
        rule.waitForIdle()
        assertNoResult()
    }

    @Test fun closeCancelsAndLateResponseDoesNotReopenScreen() {
        mount()
        waitFor { calls.get() == 1 }
        rule.onNodeWithText(rule.activity.getString(R.string.action_close)).performScrollTo().performClick()
        waitFor { cancelled.get() == 1 }
        gates[0].release.countDown()
        waitFor { finished.get() == 1 }
        rule.waitForIdle()
        assertNoResult()
        assertFalse(visible.value)
        assertEquals(1, calls.get())
    }

    @Test fun walletReplacementCancelsAndCannotAuthorizeOldTransaction() {
        mount()
        waitFor { calls.get() == 1 }
        rule.runOnIdle { session.value = "replacement-wallet-session" }
        waitFor { cancelled.get() == 1 }
        gates[0].release.countDown()
        waitFor { finished.get() == 1 }
        rule.waitForIdle()
        refresh()
        rule.waitForIdle()
        assertNoResult()
        assertEquals(1, calls.get())
    }

    @Test fun closeReopenKeepsNewResultWhenOldResponseArrivesLast() {
        mount()
        waitFor { calls.get() == 1 }
        rule.runOnIdle { visible.value = false }
        waitFor { cancelled.get() == 1 }
        rule.runOnIdle { activeService.value = service(); visible.value = true }
        waitFor { calls.get() == 2 }
        gates[1].release.countDown()
        val newFee = rule.activity.getString(R.string.tx_fee_fmt, "0.000044540001 XMR")
        rule.waitUntil(5_000) { rule.onAllNodesWithText(newFee).fetchSemanticsNodes().isNotEmpty() }
        gates[0].release.countDown()
        waitFor { finished.get() == 2 }
        rule.waitForIdle()
        rule.onNodeWithText(newFee).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.tx_fee_fmt, "0.000044540000 XMR")).assertDoesNotExist()
        assertEquals(2, calls.get())
    }

    @Test fun disabledAndNonMainnetViewsNeverCallService() {
        mount(mainnet = false)
        rule.waitForIdle()
        assertEquals(0, calls.get())
        rule.onNodeWithText(rule.activity.getString(R.string.explorer_mainnet)).assertIsDisplayed()
        rule.runOnIdle { ExplorerAccess.setEnabled(rule.activity, false) }
        rule.waitForIdle()
        assertEquals(0, calls.get())
    }

    @Test fun advancedPagesAreManualAndCancelledByCollapseOrBackground() {
        mount()
        waitFor { calls.get() == 1 }
        gates[0].release.countDown()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(blockHash).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(rule.activity.getString(R.string.explorer_conflict)).assertExists()
        assertEquals(1,calls.get()) // summary alone never prefetches advanced data
        val advanced = rule.activity.getString(R.string.explorer_advanced)
        rule.onNodeWithText(advanced).performScrollTo().performClick()
        waitFor { calls.get() == 2 }
        assertTrue(paths[1].endsWith("/details/0"))
        rule.onNodeWithText(advanced).performScrollTo().performClick()
        waitFor { cancelled.get() == 1 }
        gates[1].release.countDown(); waitFor { finished.get() == 2 }
        rule.waitForIdle()
        val version = "${rule.activity.getString(R.string.explorer_version)}: 2"
        rule.onNodeWithText(version).assertDoesNotExist()
        rule.onNodeWithText(advanced).performScrollTo().performClick()
        waitFor { calls.get() == 3 }
        gates[2].release.countDown()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(version).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(rule.activity.getString(R.string.explorer_next)).performScrollTo().performClick()
        waitFor { calls.get() == 4 }
        assertTrue(paths[3].endsWith("/details/1"))
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        waitFor { cancelled.get() == 2 }
        gates[3].release.countDown(); waitFor { finished.get() == 4 }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.waitForIdle()
        rule.onNodeWithText(version).assertDoesNotExist()
        assertNoResult(); assertEquals(4,calls.get())
    }
}
