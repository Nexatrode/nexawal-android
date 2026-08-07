package com.nexatrode.nexawal.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStallDetectionTest {

    @Test
    fun stallFlag_true() {
        assertTrue(isSyncStallError(syncStalled = true))
    }

    @Test
    fun stallFlag_false() {
        assertFalse(isSyncStallError(syncStalled = false))
    }
}
