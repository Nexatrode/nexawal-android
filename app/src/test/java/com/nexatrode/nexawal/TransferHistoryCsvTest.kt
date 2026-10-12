package com.nexatrode.nexawal

import java.io.StringWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferHistoryCsvTest {
    @Test fun rowUsesExactAmountsUtcAndEscapesCsvFields() {
        val transfer = Transfer(
            txid = "id,\"quoted\"",
            direction = "out",
            amount = 1_234_567_890_123,
            fee = 22,
            height = 42,
            timestamp = 0,
            confirmations = 3,
        )

        assertEquals(
            "out,1.234567890123,1234567890123,0.000000000022,22,42,1970-01-01 00:00:00 UTC,3,confirmed,\"id,\"\"quoted\"\"\"",
            TransferHistoryCsv.row(transfer),
        )
    }

    @Test fun exportReadsEveryMatchingPageAndRejectsRevisionChanges() {
        runBlocking {
            val writer = StringWriter()
            var calls = 0
            val count = TransferHistoryCsv.writeAll(
                walletId = "fixture",
                query = HistoryQuery(filter = "received", search = "abc", fromTimestamp = 100, toTimestamp = 200),
                writer = writer,
                fetch = { _, query ->
                    calls++
                    if (calls == 1) {
                        assertEquals(200, query.limit)
                        assertEquals(0, query.offset)
                        assertEquals("received", query.filter)
                        assertEquals("abc", query.search)
                        HistoryPage(1, "fixture", "r1", 2, 2, 0, 0, 1, null, 10, 10, 0,
                            listOf(Transfer("first", "in", 1, confirmations = 1)))
                    } else {
                        assertEquals(1, query.offset)
                        assertEquals("r1", query.revision)
                        HistoryPage(1, "fixture", "r1", 2, 2, 0, 1, null, null, 10, 10, 0,
                            listOf(Transfer("second", "in", 2, confirmations = 1)))
                    }
                },
            )
            assertEquals(2, count)
            assertEquals(2, calls)
            assertEquals(3, writer.toString().trimEnd().lineSequence().count()) // header + both rows

            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    TransferHistoryCsv.writeAll(
                        "fixture", HistoryQuery(), StringWriter(),
                        fetch = { _, query ->
                            val revision = if (query.offset == 0) "r1" else "r2"
                            HistoryPage(1, "fixture", revision, 2, 2, 0, query.offset,
                                if (query.offset == 0) 1 else null, null, 10, 10, 0,
                                listOf(Transfer("tx-${query.offset}", "in", 1, confirmations = 1)))
                        },
                    )
                }
            }
        }
    }
}
