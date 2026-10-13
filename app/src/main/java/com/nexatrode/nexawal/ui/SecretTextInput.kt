package com.nexatrode.nexawal.ui

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/** A privacy request to the selected keyboard, not a defense against a malicious keyboard. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SecretTextInput(content: @Composable () -> Unit) {
    val interceptor = remember {
        PlatformTextInputInterceptor { request, session ->
            session.startInputMethod(PlatformTextInputMethodRequest { attributes ->
                val connection = request.createInputConnection(attributes)
                attributes.imeOptions = attributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                    EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                connection
            })
        }
    }
    InterceptPlatformTextInput(interceptor, content)
}
