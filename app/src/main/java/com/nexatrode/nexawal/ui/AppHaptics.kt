package com.nexatrode.nexawal.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import com.nexatrode.nexawal.MoneroConfig

/** Quiet, opt-out tactile feedback. The platform setting is still respected by View. */
internal object AppHaptics {
    fun light(context: Context, view: View) = perform(context, view, HapticFeedbackConstants.KEYBOARD_TAP)

    fun success(context: Context, view: View) = perform(context, view, HapticFeedbackConstants.CONFIRM)

    fun failure(context: Context, view: View) = perform(context, view, HapticFeedbackConstants.REJECT)

    private fun perform(context: Context, view: View, feedbackConstant: Int) {
        if (MoneroConfig.hapticFeedbackEnabled(context)) {
            // No FLAG_IGNORE_*: respect both the view and system touch-feedback settings.
            view.performHapticFeedback(feedbackConstant)
        }
    }
}
