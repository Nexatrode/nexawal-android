package com.nexatrode.nexawal

import android.os.Handler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityLifecycleTest {
    @Test fun destroyedActivityCannotKeepAnIdleTimerForTheRetainedWallet() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        val handler = MainActivity::class.java.getDeclaredField("sessionHandler").apply {
            isAccessible = true
        }.get(activity) as Handler
        val action = MainActivity::class.java.getDeclaredField("idleLockAction").apply {
            isAccessible = true
        }.get(activity) as Runnable
        handler.postDelayed(action, 300_000)
        assertTrue(handler.hasCallbacks(action))
        controller.destroy()
        assertFalse(handler.hasCallbacks(action))
    }
}
