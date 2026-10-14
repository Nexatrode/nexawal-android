package com.nexatrode.nexawal.logic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Only already-authorized sends enter here. UI disposal cancels the waiter, not the
 * prepare→journal→relay worker; failures and completion are retained by the manager. */
class RetainedSend(private val scope: CoroutineScope) {
    private val gate = SendGate()
    fun isRunning(): Boolean = gate.isRunning()
    suspend fun <T> run(onBegin: () -> Unit, onFinish: (Throwable?) -> Unit,
        operation: suspend () -> T): T {
        currentCoroutineContext().ensureActive()
        check(gate.tryBegin()) { SendGate.ALREADY_IN_PROGRESS }
        try { onBegin() } catch (error: Throwable) { gate.end(); throw error }
        val worker = scope.async(start = CoroutineStart.UNDISPATCHED) {
            var failure: Throwable? = null
            try {
                currentCoroutineContext().ensureActive()
                operation()
            } catch (error: Throwable) {
                failure = error
                throw error
            } finally {
                try { onFinish(failure) } finally { gate.end() }
            }
        }
        return worker.await()
    }
}
