package com.nexatrode.nexawal

import org.junit.Assert.*
import org.junit.Test

class HistoryPagingTest {
    private val row = """{"txid":"abababababababababababababababababababababababababababababababab","direction":"in","amount":42,"fee":7,"confirmations":10,"is_pending":false}"""
    private fun payload() = """{"schema_version":1,"wallet_id":"fixture","revision":"42","total_count":10000,"matching_count":10000,"pending_count":0,"offset":0,"next_offset":1,"anchor_offset":0,"last_scanned_height":100,"chain_height":110,"chain_time":0,"transfers":[$row]}"""
    @Test fun boundedPageKeepsWholeLedgerCountAndExactAtomicAmounts() {
        val page = HistoryPaging.decode(payload(), "fixture")
        assertEquals(10000, page.totalCount)
        assertEquals(1, page.transfers.size)
        assertEquals(42L, page.transfers.single().amount)
        assertEquals(7L, page.transfers.single().fee)
    }
    @Test fun malformedWrongWalletAndDuplicatePagesAreRejected() {
        val source = payload()
        for (bad in listOf(
            source.replace("\"fixture\"", "\"other\""),
            source.replace("\"schema_version\":1", "\"schema_version\":2"),
            source.replace("\"next_offset\":1", "\"next_offset\":0"),
            source.replace("\"anchor_offset\":0", "\"anchor_offset\":100"),
            source.replace("\"pending_count\":0", "\"pending_count\":-1"),
            source.replace("\"revision\":\"42\"", "\"revision\":\"\""),
            source.replace("[$row]", "[$row,$row]"),
        )) {
            assertThrows(Exception::class.java) { HistoryPaging.decode(bad, "fixture") }
        }
    }
}
