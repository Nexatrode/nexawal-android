package com.nexatrode.nexawal

import android.view.Window
import android.view.WindowManager

/** Restricts capture only while recovery material or seed setup is on screen. */
internal object ScreenshotProtection {
    fun shouldProtect(seedSetupVisible: Boolean, recoveryFlowVisible: Boolean): Boolean =
        seedSetupVisible || recoveryFlowVisible

    fun apply(window: Window, protect: Boolean) {
        if (protect) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
