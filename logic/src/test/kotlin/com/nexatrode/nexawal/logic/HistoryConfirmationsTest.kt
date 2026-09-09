package com.nexatrode.nexawal.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryConfirmationsTest {
    @Test fun followsTipAdvanceAndRewindWithoutReloadingTheRow() {
        assertEquals(1L, HistoryConfirmations.count(100, false, 100, 1))
        assertEquals(10L, HistoryConfirmations.count(100, false, 109, 1))
        assertEquals(3L, HistoryConfirmations.count(100, false, 102, 10))
        assertEquals(1L, HistoryConfirmations.count(100, false, 99, 10))
    }
    @Test fun handlesPendingUnknownAndNumericBoundaries() {
        assertEquals(0L, HistoryConfirmations.count(100, true, 109, 9))
        assertEquals(9L, HistoryConfirmations.count(null, false, 109, 9))
        assertEquals(9L, HistoryConfirmations.count(100, false, 0, 9))
        assertEquals(0L, HistoryConfirmations.count(0, false, 109, 9))
        assertEquals(Long.MAX_VALUE, HistoryConfirmations.count(1, false, Long.MAX_VALUE, 0))
    }
}
