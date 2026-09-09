package com.nexatrode.nexawal.logic

import org.junit.Assert.*
import org.junit.Test

class FeeApprovalTest {
    @Test fun increasedOrInvalidFeeCannotPersistOrRelay() {
        for ((prepared, approved) in listOf(101L to 100L, -1L to 100L, 0L to -1L, Long.MAX_VALUE to Long.MAX_VALUE - 1)) {
            val events = mutableListOf<String>()
            try {
                SendSafety.withApprovedFee(prepared, approved) { events += "persist"; events += "relay" }
                fail("fee must be rejected")
            } catch (_: SendSafety.FeeApprovalException) { }
            assertTrue(events.isEmpty())
        }
    }

    @Test fun equalOrLowerFeeProceedsExactlyOnce() {
        for (fee in listOf(0L, 99L, 100L)) {
            var calls = 0
            assertEquals("signed transaction", SendSafety.withApprovedFee(fee, 100) { calls++; "signed transaction" })
            assertEquals(1, calls)
        }
    }
}
