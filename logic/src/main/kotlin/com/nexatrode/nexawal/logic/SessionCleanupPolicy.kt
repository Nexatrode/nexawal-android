package com.nexatrode.nexawal.logic

import kotlinx.coroutines.delay

/** A request arriving during a suspend/retry must be replayed, never lost behind single-flight. */
class SessionCleanupRequests {
    data class Snapshot(val generation: Long, val close: Boolean, val continueRefresh: Boolean)
    private var current = Snapshot(0, false, false)
    @Synchronized fun request(close: Boolean, continueRefresh: Boolean) {
        val mustClose = current.close || close
        current = Snapshot(current.generation + 1, mustClose, !mustClose && continueRefresh)
    }
    @Synchronized fun snapshot(): Snapshot = current
    @Synchronized fun finish(generation: Long): Snapshot? {
        if (current.generation != generation) return null
        val completed = current
        current = Snapshot(current.generation, false, false)
        return completed
    }
    @Synchronized fun clearClosed() { current = Snapshot(current.generation, false, false) }
}

object SessionCleanupPolicy {
    // JNI includes the return code separately from daemon-controlled diagnostic text.
    fun isTimeout(error: Throwable): Boolean =
        error is RuntimeException &&
            Regex("^wallet_(seal|close) \\(rc=-31\\)(:|$)").containsMatchIn(error.message.orEmpty())

    suspend fun complete(retryDelayMs: Long = 1000, operation: suspend () -> Unit) {
        while (true) {
            try { operation(); return }
            catch (error: Throwable) {
                if (!isTimeout(error)) throw error
                delay(retryDelayMs)
            }
        }
    }
}
