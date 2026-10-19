package com.nexatrode.nexawal.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.nexatrode.nexawal.MoneroConfig

/** Optional send-result cues. No audio is played unless the user enables them. */
internal object SendSounds {
    fun success(context: Context) = play(context, ToneGenerator.TONE_PROP_ACK)

    fun failure(context: Context) = play(context, ToneGenerator.TONE_PROP_NACK)

    private fun play(context: Context, tone: Int) {
        if (!MoneroConfig.sendSoundsEnabled(context)) return

        val generator = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35) }
            .getOrNull() ?: return
        val started = runCatching { generator.startTone(tone, 180) }.getOrDefault(false)
        if (!started) {
            generator.release()
            return
        }

        // ToneGenerator owns native audio resources; release shortly after this brief cue.
        Handler(Looper.getMainLooper()).postDelayed({ generator.release() }, 240L)
    }
}
