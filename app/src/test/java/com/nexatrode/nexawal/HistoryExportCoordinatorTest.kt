package com.nexatrode.nexawal
import android.net.Uri
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HistoryExportCoordinatorTest {
    @Test fun chooserResultSurvivesLockAndRequiresSameUnlockedWallet() {
        val export = HistoryExportCoordinator()
        val file = File.createTempFile("history-export-test-", ".csv")
        export.stage(file, "wallet-a")
        export.selected(Uri.parse("content://test/export"))
        assertNull(export.claim(false, "wallet-a"))
        assertTrue(file.exists())
        assertEquals(file, export.claim(true, "wallet-a")!!.file)
        assertNull(export.claim(true, "wallet-a"))
        file.delete()
    }
    @Test fun cancellationAndWalletReplacementDiscardSensitiveFile() {
        for (cancel in listOf(true, false)) {
            val export = HistoryExportCoordinator()
            val file = File.createTempFile("history-export-test-", ".csv")
            export.stage(file, "wallet-a")
            export.selected(if (cancel) null else Uri.parse("content://test/export"))
            assertNull(export.claim(true, "wallet-b"))
            assertFalse(file.exists())
        }
    }
}
