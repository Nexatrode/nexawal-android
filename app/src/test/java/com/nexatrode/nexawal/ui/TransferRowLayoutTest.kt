package com.nexatrode.nexawal.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.nexatrode.nexawal.Transfer
import com.nexatrode.nexawal.ui.theme.NexawalTheme
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w480dp-h1200dp-night-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransferRowLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val originalLocale = Locale.getDefault()
    private val originalTimeZone = TimeZone.getDefault()
    private val historicalTransfer = Transfer(
        txid = "a".repeat(64),
        direction = "in",
        amount = 29_000_000,
        fee = 30_000_000,
        timestamp = Instant.parse("2025-11-04T20:12:00Z").epochSecond,
        confirmations = 219_074,
    )

    @Before
    fun setLocale() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun historicalMetadataDoesNotCollapseIntoSingleCharacterColumns() {
        var phoneWidth by mutableStateOf(360)
        var fontScale by mutableStateOf(1f)
        var techno by mutableStateOf(false)
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                NexawalTheme(darkTheme = true) {
                    val palette = rememberNexaPalette(techno)
                    // The screen and history card each have 16dp horizontal padding.
                    Box(Modifier.width(phoneWidth.dp).background(palette.card).padding(horizontal = 32.dp)) {
                        TransferRow(historicalTransfer, palette) {}
                    }
                }
            }
        }

        for (width in listOf(360, 320, 412)) {
            for (scale in listOf(1f, 1.3f, 2f)) {
                for (neon in listOf(false, true)) {
                    composeRule.runOnIdle {
                        phoneWidth = width
                        fontScale = scale
                        techno = neon
                    }
                    composeRule.onNodeWithText("219,074 conf", useUnmergedTree = true).assertExists()
                    composeRule.onNodeWithText("+ 0.000029 XMR", useUnmergedTree = true).assertExists()
                    composeRule.onNodeWithText("Fee 0.000030", useUnmergedTree = true).assertExists()
                    assertReadableLayout("width=$width, fontScale=$scale, techno=$neon")
                    if (width == 360 && scale != 1.3f) {
                        savePreview("history-font-$scale-techno-$neon")
                    }
                }
            }
        }
    }

    @Test
    fun pendingSendWithoutDateOrFeeStillShowsSignedAmountAndOpensDetails() {
        var clicked = false
        composeRule.setContent {
            NexawalTheme {
                Box(Modifier.width(320.dp).padding(horizontal = 32.dp)) {
                    TransferRow(
                        historicalTransfer.copy(
                            direction = "out", amount = 123_456_789_000_000,
                            timestamp = null, fee = null, confirmations = 0, isPending = true,
                        ),
                        rememberNexaPalette(false),
                        onClick = { clicked = true },
                    )
                }
            }
        }
        composeRule.onNodeWithText("Pending", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("\u2212 123.456789 XMR", useUnmergedTree = true).assertExists()
        assertReadableLayout("pending send")
        composeRule.onNodeWithTag(A11yTags.TRANSFER_ROW).performClick()
        composeRule.runOnIdle { assertTrue(clicked) }
    }

    private fun assertReadableLayout(scenario: String) {
        val textNodes = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
            useUnmergedTree = true,
        )
        val count = textNodes.fetchSemanticsNodes().size
        val row = composeRule.onNodeWithTag(A11yTags.TRANSFER_ROW).fetchSemanticsNode().boundsInRoot
        val bounds = (0 until count).map { index ->
            val results = mutableListOf<TextLayoutResult>()
            textNodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertEquals(1, results.size)
            val layout = results.single()
            val label = layout.layoutInput.text.text
            assertTrue("$scenario: '$label' wraps to ${layout.lineCount} lines", layout.lineCount <= 2)
            // The abbreviated txid may ellipsize; amounts, dates, fees and status must stay readable.
            if (!label.contains('…')) {
                assertFalse("$scenario: '$label' is vertically clipped", layout.didOverflowHeight)
                for (line in 0 until layout.lineCount) {
                    assertFalse("$scenario: '$label' is ellipsized", layout.isLineEllipsized(line))
                    assertTrue(
                        "$scenario: '$label' is horizontally clipped",
                        layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f,
                    )
                }
            }
            val rect = textNodes[index].fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$scenario: '$label' lies outside the row",
                rect.left >= row.left && rect.right <= row.right && rect.top >= row.top && rect.bottom <= row.bottom,
            )
            label to rect
        }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                assertFalse(
                    "$scenario: '${bounds[i].first}' overlaps '${bounds[j].first}'",
                    bounds[i].second.overlaps(bounds[j].second),
                )
            }
        }
    }

    private fun savePreview(name: String) {
        val preview = File("build/reports/transfer-row/$name.png")
        requireNotNull(preview.parentFile).mkdirs()
        composeRule.runOnIdle {
            val view = composeRule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            preview.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
