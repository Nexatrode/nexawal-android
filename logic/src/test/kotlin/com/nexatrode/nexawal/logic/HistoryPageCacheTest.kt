package com.nexatrode.nexawal.logic
import org.junit.Assert.*
import org.junit.Test
class HistoryPageCacheTest {
    @Test fun tenThousandRowsStayBoundedAndReload() {
        val cache = HistoryPageCache<Int>()
        for (offset in 0 until 10000 step 50) {
            cache.insert(offset, (offset until offset + 50).toList())
            assertTrue(cache.storedRowCount <= 200)
            assertEquals(offset + 49, cache.row(offset + 49))
        }
        assertNull(cache.row(0))
        cache.insert(0, (0 until 50).toList())
        assertEquals(0, cache.row(0))
        assertTrue(cache.storedRowCount <= 200)
        cache.clear(); assertNull(cache.row(0))
    }
}
