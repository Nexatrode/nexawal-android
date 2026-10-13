package com.nexatrode.nexawal
import android.net.Uri
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HistoryExportCoordinatorTest {
    @Test fun chooserResultSurvivesLockAndRequiresSameUnlockedWallet() = runBlocking {
        val export = HistoryExportCoordinator(this)
        val file = File.createTempFile("history-export-test-", ".csv")
        export.stage(file, "wallet-a")
        export.selected(Uri.parse("content://test/export"))
        assertNull(export.startIfReady(false, "wallet-a") { fail("locked export") })
        assertTrue(file.exists())
        export.startIfReady(true, "wallet-a") { assertEquals(file, it.file) }!!.join()
        assertNull(export.startIfReady(true, "wallet-a") { fail("duplicate export") })
        assertFalse(file.exists())
    }
    @Test fun cancellationAndWalletReplacementDiscardSensitiveFile() {
        for (cancel in listOf(true, false)) {
            val export = HistoryExportCoordinator()
            val file = File.createTempFile("history-export-test-", ".csv")
            export.stage(file, "wallet-a")
            export.selected(if (cancel) null else Uri.parse("content://test/export"))
            assertNull(export.startIfReady(true, "wallet-b") { fail("foreign export") })
            assertFalse(file.exists())
        }
    }

    @Test fun authorizedCopySurvivesCallerCancellationRelockAndRecreation() = runBlocking {
        val export = HistoryExportCoordinator(this)
        val file = File.createTempFile("history-export-test-", ".csv")
        file.writeText("all rows")
        export.stage(file, "wallet-a")
        export.selected(Uri.parse("content://test/export"))
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val copied = CompletableDeferred<String>()
        var worker: Job? = null
        val ui = launch {
            worker = export.startIfReady(true, "wallet-a") { pending ->
                started.complete(Unit)
                resume.await()
                copied.complete(pending.file.readText())
            }
        }
        started.await()
        ui.cancel()
        export.discard() // screen/chooser disposal must not delete the active source
        assertTrue(file.exists())
        assertNull(export.startIfReady(false, "wallet-a") { fail("locked retry") })
        assertNull(export.startIfReady(true, "wallet-a") { fail("duplicate worker") })
        resume.complete(Unit)
        worker!!.join()
        assertEquals("all rows", copied.await())
        assertFalse(file.exists())
        assertFalse(export.failed.value)
    }

    @Test fun writerFailureIsReportedAndAllowsANewExport() = runBlocking {
        val export = HistoryExportCoordinator(this)
        val file = File.createTempFile("history-export-test-", ".csv")
        export.stage(file, "wallet-a")
        export.selected(Uri.parse("content://test/export"))
        export.startIfReady(true, "wallet-a") { error("disk full") }!!.join()
        assertFalse(file.exists())
        assertTrue(export.failed.value)
        export.acknowledgeFailure()
        assertFalse(export.failed.value)
        val next = File.createTempFile("history-export-test-", ".csv")
        export.stage(next, "wallet-a")
        export.discard()
        assertFalse(next.exists())
    }

    @Test fun cancelledScopeBeforeWorkerEntryCleansUpSource() = runBlocking {
        val parent = Job()
        parent.cancel()
        val export = HistoryExportCoordinator(CoroutineScope(coroutineContext + parent))
        val file = File.createTempFile("history-export-test-", ".csv")
        export.stage(file, "wallet-a")
        export.selected(Uri.parse("content://test/export"))
        export.startIfReady(true, "wallet-a") { fail("cancelled worker") }!!.join()
        assertFalse(file.exists())
        assertFalse(export.failed.value)
    }
}
