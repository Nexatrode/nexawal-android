package com.nexatrode.nexawal.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreHeightPolicyTest {
    @Test fun syncedCuprateOrMonerodUsesHeightWhenTargetIsZero() {
        assertEquals(3_783_466L, RestoreHeightPolicy.suggestedCreateHeight(3_783_476, 0))
    }
    @Test fun positiveTargetAndGenesisRemainSupported() {
        assertEquals(990L, RestoreHeightPolicy.suggestedCreateHeight(900, 1_000))
        assertEquals(0L, RestoreHeightPolicy.suggestedCreateHeight(1, 0))
    }
    @Test(expected = IllegalArgumentException::class) fun absentHeightsAreAnErrorNotASuccessfulZeroSuggestion() {
        RestoreHeightPolicy.suggestedCreateHeight(0, 0)
    }
    @Test(expected = IllegalArgumentException::class) fun negativeHeightsAreRejected() {
        RestoreHeightPolicy.suggestedCreateHeight(-1, 10)
    }
}
