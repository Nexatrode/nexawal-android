package com.nexatrode.nexawal.logic
import org.junit.Assert.*
import org.junit.Test

class SendPreviewIntentTest {
    @Test fun recoveredPaymentCannotContinueToNewPreparation() {
        var preparations = 0
        try {
            SendSafety.requireNewPayment("previous-txid")
            preparations++
            fail("Recovery must stop the new payment")
        } catch (recovered: SendSafety.PreviousPaymentRecovered) {
            assertEquals("previous-txid", recovered.txid)
        }
        assertEquals(0, preparations)
        SendSafety.requireNewPayment(null)
    }
    @Test fun everyAuthorityInputInvalidatesPreview() {
        val approved = SendPreviewIntent("a", 10, "wallet:1", "node", false)
        for (changed in listOf(approved.copy(destination="b"), approved.copy(amount=11), approved.copy(session="wallet:2"), approved.copy(node="other"), approved.copy(maximum=true, amount=null))) {
            assertNotEquals(approved, changed)
        }
    }
    @Test fun serverDecimalsAreBoundedBeforeFormatting() {
        assertNotNull(FiatEstimate.serverRate("547.380000000000"))
        for (value in listOf("1e1000000000", "1e-1000000000", "NaN", "0", "-1", "1." + "1".repeat(1000), "9".repeat(1000))) {
            assertNull(value, FiatEstimate.serverRate(value))
        }
    }
}
