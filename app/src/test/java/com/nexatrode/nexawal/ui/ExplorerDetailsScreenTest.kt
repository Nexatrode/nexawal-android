package com.nexatrode.nexawal.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.nexatrode.nexawal.ExplorerAccess
import com.nexatrode.nexawal.ExplorerService
import com.nexatrode.nexawal.MoneroConfig
import com.nexatrode.nexawal.R
import com.nexatrode.nexawal.ui.theme.NexawalTheme
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fr-rFR-w360dp-h740dp-night-xhdpi")
class ExplorerDetailsScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    @Test fun disabledFrenchViewIsLocalizedAndMakesNoRequests() {
        val context = composeRule.activity
        ExplorerAccess.setEnabled(context, false)
        MoneroConfig.setTechnoThemeEnabled(context, true)
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun callStart(call: Call) { requests.incrementAndGet() }
        }).build()
        val service = ExplorerService(context, client)
        composeRule.setContent {
            NexawalTheme(darkTheme = true) { ExplorerDetailsScreen("a".repeat(64), true, service) {} }
        }
        composeRule.onNodeWithText(context.getString(R.string.explorer_details)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.explorer_disabled)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, requests.get()) }
        composeRule.runOnIdle { ExplorerAccess.setEnabled(context, true) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("Enabling alone must not query even while mounted", 0, requests.get())
            ExplorerAccess.setEnabled(context, false)
        }
    }
}
