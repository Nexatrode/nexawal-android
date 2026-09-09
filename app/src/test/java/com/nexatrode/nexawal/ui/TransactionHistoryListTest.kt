package com.nexatrode.nexawal.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.nexatrode.nexawal.*
import com.nexatrode.nexawal.ui.theme.NexawalTheme
import java.io.File
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h740dp-night-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransactionHistoryListTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun confirmationsAdvanceAndRewindWithoutFetchingOrClearingPages() {
        val height = mutableLongStateOf(100)
        var calls = 0
        val pager = TransactionsPager { _, q ->
            calls++
            HistoryPage(1, "fixture", "1", 1, 1, 0, 0, null, null, 100, 100, 0,
                listOf(Transfer(txid = "a".repeat(64), direction = "in", amount = 1, height = 100, confirmations = 1)))
        }
        composeRule.setContent {
            NexawalTheme(darkTheme = true) {
                LaunchedEffect(Unit) { pager.reset("fixture", HistoryQuery()) }
                TransactionHistoryList(pager, rememberNexaPalette(false), rememberLazyListState(),
                    modifier = Modifier.fillMaxSize(), chainHeight = height.longValue) {}
            }
        }
        composeRule.waitUntil(10000) { pager.count == 1 && !pager.loading }
        composeRule.onAllNodesWithText("1 conf", substring = true).assertCountEquals(1)
        composeRule.runOnIdle { height.longValue = 109 }
        composeRule.onAllNodesWithText("10 conf", substring = true).assertCountEquals(1)
        composeRule.runOnIdle { height.longValue = 102 }
        composeRule.onAllNodesWithText("3 conf", substring = true).assertCountEquals(1)
        composeRule.runOnIdle {
            assertEquals(1, calls)
            assertEquals("1", pager.revision)
            assertEquals(1, pager.cache.storedRowCount)
            assertFalse(pager.changed)
            assertEquals(10L, pager.cache.row(0)!!.atChainHeight(109).confirmations)
        }
    }

    @Test fun tenThousandRowsScrollForwardAndBackWithoutKeepingThemAll() {
        val pager = TransactionsPager { _, q ->
            val rows = (q.offset until minOf(q.offset + 50, 10000)).map { i ->
                Transfer(txid = "%064x".format(i), direction = if (i % 2 == 0) "in" else "out",
                    amount = 29_000_000, fee = 30_000_000, timestamp = 1786000000, confirmations = 219074)
            }
            HistoryPage(1, "fixture", "1", 10000, 10000, 0, q.offset,
                (q.offset + rows.size).takeIf { it < 10000 }, null, 100, 110, 0, rows)
        }
        composeRule.setContent {
            NexawalTheme(darkTheme = true) {
                val palette = rememberNexaPalette(true)
                LaunchedEffect(Unit) { pager.reset("fixture", HistoryQuery()) }
                TransactionHistoryList(pager, palette, rememberLazyListState(),
                    modifier = Modifier.fillMaxSize().background(palette.background).padding(16.dp)) {}
            }
        }
        composeRule.waitUntil(10000) { pager.count == 10000 && !pager.loading }
        for (index in listOf(9500, 400, 9950, 0)) {
            composeRule.onNodeWithTag("transaction-history-list").performScrollToIndex(index)
            composeRule.waitUntil(10000) { pager.cache.row(index) != null && !pager.loading }
            composeRule.runOnIdle {
                assertTrue(pager.cache.storedRowCount <= 200)
                assertNull(pager.error)
                assertFalse(pager.changed)
            }
        }
        composeRule.runOnIdle {
            val view = composeRule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/reports/transaction-history/ten-thousand-neon.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
