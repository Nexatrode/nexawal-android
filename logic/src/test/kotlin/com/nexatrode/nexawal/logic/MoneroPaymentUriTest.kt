package com.nexatrode.nexawal.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneroPaymentUriTest {
    private val primary =
        "4B33mFPMq6mKi7Eiyd5XuyKRVMGVZz1Rqb9ZTyGApXW5d1aT7UBDZ89ewmnWFkzJ5wPd2SFbn313vCT8a4E2Qf4KQH4pNey"

    @Test
    fun addressExtracted() {
        val parsed = MoneroPaymentUri.parse("monero:$primary")
        assertEquals(primary, parsed?.address)
        assertNull(parsed?.amountXmr)
    }

    @Test
    fun amountExtracted() {
        val parsed = MoneroPaymentUri.parse("monero:$primary?tx_amount=1.5")
        assertEquals(primary, parsed?.address)
        assertEquals("1.5", parsed?.amountXmr)
    }

    @Test
    fun spendAndViewKeysIgnoredAsSendTargets() {
        val uri = "monero:$primary?spend_key=deadbeefdeadbeef&view_key=cafebabecafebabe&tx_amount=1.0"
        val parsed = MoneroPaymentUri.parse(uri)
        assertEquals(primary, parsed?.address)
        assertEquals("1.0", parsed?.amountXmr)
        assertNotEquals("deadbeefdeadbeef", parsed?.address)
        assertNotEquals("cafebabecafebabe", parsed?.address)
    }

    @Test
    fun slashSlashPrefix() {
        val parsed = MoneroPaymentUri.parse("monero://$primary?amount=0.25")
        assertEquals(primary, parsed?.address)
        assertEquals("0.25", parsed?.amountXmr)
    }

    @Test
    fun nonMoneroRejected() {
        assertNull(MoneroPaymentUri.parse(primary))
        assertNull(MoneroPaymentUri.parse("bitcoin:$primary"))
    }
}
