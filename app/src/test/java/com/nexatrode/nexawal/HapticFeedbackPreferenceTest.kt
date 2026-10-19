package com.nexatrode.nexawal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HapticFeedbackPreferenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun defaultsOnAndPersistsOptOut() {
        context.getSharedPreferences("monero_config", Context.MODE_PRIVATE)
            .edit().clear().commit()

        assertTrue(MoneroConfig.hapticFeedbackEnabled(context))
        MoneroConfig.setHapticFeedbackEnabled(context, false)
        assertFalse(MoneroConfig.hapticFeedbackEnabled(context))
        MoneroConfig.setHapticFeedbackEnabled(context, true)
        assertTrue(MoneroConfig.hapticFeedbackEnabled(context))
    }

    @Test
    fun sendSoundsDefaultOffAndPersistOptIn() {
        context.getSharedPreferences("monero_config", Context.MODE_PRIVATE)
            .edit().clear().commit()

        assertFalse(MoneroConfig.sendSoundsEnabled(context))
        MoneroConfig.setSendSoundsEnabled(context, true)
        assertTrue(MoneroConfig.sendSoundsEnabled(context))
        MoneroConfig.setSendSoundsEnabled(context, false)
        assertFalse(MoneroConfig.sendSoundsEnabled(context))
    }
}
