package com.nexatrode.nexawal

import org.junit.Assert.*
import org.junit.Test

class PreparedSendBindingTest {
    @Test fun journalRoundTripPreservesWalletBinding() {
        val raw = """{"txid":"fixture","amount":1,"fee":2,"signed_tx_hex":"00","wallet_binding":"primary-address-with-network"}"""
        val prepared = SendJson.decodePreparedSend(raw)
        assertEquals("primary-address-with-network", prepared.walletBinding)
        val encoded = SendJson.encodePreparedSend(prepared)
        assertTrue(encoded.contains("\"wallet_binding\""))
        assertEquals(prepared, SendJson.decodePreparedSend(encoded))
    }

    @Test fun legacyJournalRemainsReadableForExplicitRecovery() {
        val raw = """{"txid":"fixture","amount":1,"fee":2,"signed_tx_hex":"00"}"""
        assertNull(SendJson.decodePreparedSend(raw).walletBinding)
        // Native relay, not the JSON decoder, refuses to relay an unbound journal.
    }
}
