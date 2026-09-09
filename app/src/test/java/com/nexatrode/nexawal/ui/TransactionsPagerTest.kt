package com.nexatrode.nexawal.ui

import com.nexatrode.nexawal.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class TransactionsPagerTest {
    private fun page(q: HistoryQuery, total: Int = 10000, revision: String = "1"): HistoryPage {
        val offset = if (q.anchorTxid != null) 4350 else q.offset
        val rows = (offset until minOf(offset + q.limit, total)).map {
            Transfer(txid = "%064x".format(it), direction = "in", amount = it.toLong(), confirmations = 10)
        }
        return HistoryPage(1, "fixture", revision, total, total, 0, offset,
            (offset + rows.size).takeIf { it < total }, if (q.anchorTxid != null) 4351 else null,
            100, 110, 0, rows)
    }

    @Test fun allScalesStayBoundedAndEvictedPagesReload() = runBlocking {
        for (size in listOf(0, 1, 10, 50, 100, 1000, 10000)) {
            var calls = 0
            val pager = TransactionsPager { _, q -> calls++; page(q, size) }
            pager.reset("fixture", HistoryQuery())
            assertEquals(size, pager.total)
            for (offset in 0 until size step 50) {
                pager.load(offset)
                assertNotNull(pager.cache.row(offset))
                assertTrue(pager.cache.storedRowCount <= 200)
            }
            if (size > 200) {
                assertNull(pager.cache.row(0))
                val before = calls
                pager.load(0)
                assertEquals(before + 1, calls)
                assertNotNull(pager.cache.row(0))
            }
        }
    }

    @Test fun lateResultsCannotPopulateCancelledOrReplacementQuery() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val pager = TransactionsPager { _, q ->
            if (q.search == "old") { started.complete(Unit); finish.await() }
            page(q, if (q.search == "old") 10000 else 1)
        }
        val old = launch { pager.reset("fixture", HistoryQuery(search = "old")) }
        started.await()
        pager.reset("fixture", HistoryQuery(search = "new"))
        finish.complete(Unit); old.join()
        assertEquals(1, pager.count)
        assertEquals(1, pager.cache.storedRowCount)
        assertFalse(pager.loading)
    }

    @Test fun failedPageRetriesAndStaleRevisionRequiresReload() = runBlocking {
        var fail = true
        val pager = TransactionsPager { _, q ->
            if (q.offset == 50 && fail) error("temporary read failure")
            if (q.offset == 100) error("stale_history_cursor")
            page(q)
        }
        pager.reset("fixture", HistoryQuery())
        pager.load(50)
        assertNotNull(pager.error)
        assertEquals(10000, pager.total)
        assertNotNull(pager.cache.row(0))
        fail = false
        pager.retry()
        assertNull(pager.error)
        assertNotNull(pager.cache.row(50))
        pager.load(100)
        assertTrue(pager.changed)
        assertNotNull(pager.cache.row(0))
    }

    @Test fun anchorIsOnlyUsedForReloadNotSubsequentPages() = runBlocking {
        val requests = mutableListOf<HistoryQuery>()
        val pager = TransactionsPager { _, q -> requests.add(q); page(q) }
        pager.reset("fixture", HistoryQuery(), "%064x".format(4351))
        assertEquals(4351, pager.anchorIndex)
        assertNotNull(pager.cache.row(4351))
        pager.load(4400)
        assertNull(requests.last().anchorTxid)
        assertEquals(4400, requests.last().offset)
        assertEquals("1", requests.last().revision)
    }
}
