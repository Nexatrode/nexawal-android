package com.nexatrode.nexawal.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmrAmountTest {
    @Test
    fun oneXmr() {
        assertEquals(1_000_000_000_000L, XmrAmount.parsePiconero("1.0"))
        assertEquals(1_000_000_000_000L, XmrAmount.parsePiconero("1"))
    }

    @Test
    fun onePiconero() {
        assertEquals(1L, XmrAmount.parsePiconero("0.000000000001"))
    }

    @Test
    fun overflowRejected() {
        assertNull(XmrAmount.parsePiconero("18446745"))
        assertNull(XmrAmount.parsePiconero("18446744073710.0"))
        assertNull(XmrAmount.parsePiconero("999999999999999"))
    }

    @Test
    fun invalidRejected() {
        assertNull(XmrAmount.parsePiconero(""))
        assertNull(XmrAmount.parsePiconero("abc"))
        assertNull(XmrAmount.parsePiconero("1.2.3"))
        assertNull(XmrAmount.parsePiconero("0.0000000000001"))
    }
}
