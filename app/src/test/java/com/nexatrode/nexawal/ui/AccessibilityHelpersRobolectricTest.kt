package com.nexatrode.nexawal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM Compose semantics checks via Robolectric — runs in CI without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityHelpersRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val palette = NexaPalette(
        background = Color.White,
        card = Color(0xFFF2F2F7),
        primaryText = Color.Black,
        secondaryText = Color.DarkGray,
        separator = Color.LightGray,
        accent = Color(0xFF007AFF),
        secondaryAction = Color(0xFFE5E5EA),
        success = Color(0xFF34C759),
        danger = Color(0xFFFF3B30),
        border = Color.Gray,
        cta = Color(0xFFFF6B35),
        ctaText = Color.White,
        classic = false,
        isLight = true,
    )

    @Test
    fun labeledSwitchRow_isToggleableAndTagged() {
        var checked by mutableStateOf(true)
        composeRule.setContent {
            LabeledSwitchRow(
                label = "Mainnet",
                checked = checked,
                onCheckedChange = { checked = it },
                palette = palette,
                testTag = A11yTags.CREATE_MAINNET_SWITCH,
            )
        }

        composeRule.onNodeWithTag(A11yTags.CREATE_MAINNET_SWITCH)
            .assertIsToggleable()
            .assertIsOn()
            .performClick()

        composeRule.onNodeWithTag(A11yTags.CREATE_MAINNET_SWITCH).assertIsOff()
    }

    @Test
    fun politeStatus_exposesStatusLiveTag() {
        composeRule.setContent {
            Text("Saved node", modifier = Modifier.a11yPoliteStatus())
        }
        composeRule.onNodeWithTag(A11yTags.STATUS_LIVE).assertExists()
        composeRule.onNodeWithText("Saved node").assertExists()
    }

    @Test
    fun assertiveError_exposesErrorLiveTag() {
        composeRule.setContent {
            Text("Something failed", modifier = Modifier.a11yAssertiveError())
        }
        composeRule.onNodeWithTag(A11yTags.ERROR_LIVE).assertExists()
    }

    @Test
    fun syncProgress_exposesProgressSemantics() {
        composeRule.setContent {
            Column(
                modifier = Modifier.a11ySyncProgress(0.42f, "Sync progress 42 percent"),
            ) {
                Text("progress")
            }
        }
        val nodes = composeRule.onNodeWithTag(A11yTags.SYNC_PROGRESS).fetchSemanticsNode()
        assertTrue(
            nodes.config.contains(SemanticsProperties.ProgressBarRangeInfo) ||
                nodes.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .any { it.contains("42") },
        )
    }

    @Test
    fun radioOption_exposesRadioRoleAndSelected() {
        composeRule.setContent {
            Text(
                "Clearnet only",
                modifier = Modifier.a11yRadioOption(selected = true, label = "Clearnet only"),
            )
        }
        val node = composeRule.onNodeWithText("Clearnet only").fetchSemanticsNode()
        val roleMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        assertTrue(roleMatcher.matches(node))
        assertTrue(node.config.getOrElse(SemanticsProperties.Selected) { false })
    }
}
