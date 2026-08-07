package com.nexatrode.nexawal

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.nexatrode.nexawal.ui.A11yTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose semantics checks for TalkBack-oriented accessibility wiring.
 *
 * These prefer stable [A11yTags] over brittle layout coordinates. Some assertions
 * are conditional on whether a wallet is already open on the device under test.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AccessibilitySemanticsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun a11y_stringResources_arePresent() {
        val ctx = composeRule.activity
        assertTrue(ctx.getString(R.string.a11y_camera_preview).isNotBlank())
        assertTrue(ctx.getString(R.string.a11y_sync_progress_fmt, 42).contains("42"))
        assertTrue(ctx.getString(R.string.scan_qr_cd).isNotBlank())
        assertTrue(ctx.getString(R.string.receive_qr_cd).isNotBlank())
        assertTrue(ctx.getString(R.string.nav_wallet).isNotBlank())
    }

    @Test
    fun setupOrWallet_exposesPrimaryA11ySurface() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            hasTag(A11yTags.CREATE_MAINNET_SWITCH) || hasTag(A11yTags.BOTTOM_NAV)
        }

        when {
            hasTag(A11yTags.CREATE_MAINNET_SWITCH) -> {
                composeRule.onNodeWithTag(A11yTags.CREATE_MAINNET_SWITCH)
                    .assertIsDisplayed()
                    .assertIsToggleable()
                // Mainnet defaults on.
                composeRule.onNodeWithTag(A11yTags.CREATE_MAINNET_SWITCH).assertIsOn()

                composeRule.onNodeWithTag(A11yTags.DEVICE_AUTH_SWITCH)
                    .assertIsDisplayed()
                    .assertIsToggleable()

                // Visible create/import chrome still labeled for TalkBack.
                composeRule.onNode(
                    hasText(composeRule.activity.getString(R.string.section_wallet_setup), substring = true)
                        .or(hasText(composeRule.activity.getString(R.string.create_wallet), substring = true)),
                ).assertIsDisplayed()
            }
            else -> {
                composeRule.onNodeWithTag(A11yTags.BOTTOM_NAV).assertIsDisplayed()
                val wallet = composeRule.activity.getString(R.string.nav_wallet)
                composeRule.onNodeWithText(wallet, substring = true).assertIsDisplayed()
            }
        }
    }

    @Test
    fun openWallet_settingsClassicSwitch_isToggleable_whenReachable() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            hasTag(A11yTags.CREATE_MAINNET_SWITCH) || hasTag(A11yTags.BOTTOM_NAV)
        }
        if (!hasTag(A11yTags.BOTTOM_NAV)) return

        val settings = composeRule.activity.getString(R.string.nav_settings)
        composeRule.onNodeWithText(settings, substring = true).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasTag(A11yTags.CLASSIC_UI_SWITCH)
        }
        composeRule.onNodeWithTag(A11yTags.CLASSIC_UI_SWITCH)
            .assertIsDisplayed()
            .assertIsToggleable()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasTag(A11yTags.NETWORK_POLICY)
        }
        composeRule.onNodeWithTag(A11yTags.NETWORK_POLICY).assertIsDisplayed()
    }

    @Test
    fun openWallet_sendScanQr_hasContentDescription_whenReachable() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            hasTag(A11yTags.CREATE_MAINNET_SWITCH) || hasTag(A11yTags.BOTTOM_NAV)
        }
        if (!hasTag(A11yTags.BOTTOM_NAV)) return

        val send = composeRule.activity.getString(R.string.nav_send)
        composeRule.onNodeWithText(send, substring = true).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasTag(A11yTags.SCAN_QR)
        }
        val scanCd = composeRule.activity.getString(R.string.scan_qr_cd)
        composeRule.onNodeWithTag(A11yTags.SCAN_QR).assertIsDisplayed()
        composeRule.onNode(hasContentDescription(scanCd)).assertIsDisplayed()
    }

    @Test
    fun openWallet_receiveQr_hasContentDescription_whenReachable() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            hasTag(A11yTags.CREATE_MAINNET_SWITCH) || hasTag(A11yTags.BOTTOM_NAV)
        }
        if (!hasTag(A11yTags.BOTTOM_NAV)) return

        val receive = composeRule.activity.getString(R.string.nav_receive)
        composeRule.onNodeWithText(receive, substring = true).performClick()

        composeRule.waitUntil(timeoutMillis = 8_000) {
            hasTag(A11yTags.RECEIVE_QR) ||
                composeRule.onAllNodes(
                    hasContentDescription(composeRule.activity.getString(R.string.receive_qr_cd)),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty()
        }

        val qrCd = composeRule.activity.getString(R.string.receive_qr_cd)
        // QR may take a moment to render once address loads.
        runCatching {
            composeRule.onNodeWithTag(A11yTags.RECEIVE_QR, useUnmergedTree = true).assertIsDisplayed()
        }.recoverCatching {
            composeRule.onNode(hasContentDescription(qrCd), useUnmergedTree = true).assertIsDisplayed()
        }.getOrThrow()
    }

    @Test
    fun transferRow_usesButtonRole_whenRowsExist() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            hasTag(A11yTags.CREATE_MAINNET_SWITCH) || hasTag(A11yTags.BOTTOM_NAV)
        }
        if (!hasTag(A11yTags.BOTTOM_NAV)) return

        val wallet = composeRule.activity.getString(R.string.nav_wallet)
        composeRule.onNodeWithText(wallet, substring = true).performClick()

        // Optional: only assert when the device wallet already has transfers.
        val rows = composeRule.onAllNodesWithTag(A11yTags.TRANSFER_ROW).fetchSemanticsNodes()
        if (rows.isEmpty()) return

        val buttonMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        assertTrue(
            "Transfer rows should expose Role.Button",
            rows.any { buttonMatcher.matches(it) },
        )
        assertTrue(
            "Transfer rows should have a spoken name",
            rows.any { node ->
                node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .any { it.isNotBlank() }
            },
        )
    }

    private fun hasTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
}
