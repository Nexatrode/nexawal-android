package com.nexatrode.nexawal.logic

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Activity-owned access state. Never restore it from a saved bundle or a retained wallet ID. */
class WalletAccessGate {
    enum class State { LOCKED, UNLOCKING, OPEN }

    private val mutableState = MutableStateFlow(State.LOCKED)
    private val attempts = SessionAuthorityEpoch()
    val state: StateFlow<State> = mutableState.asStateFlow()

    @Synchronized fun lock() {
        attempts.invalidate()
        mutableState.value = State.LOCKED
    }

    @Synchronized private fun begin(): Long? {
        if (!mutableState.compareAndSet(State.LOCKED, State.UNLOCKING)) return null
        return attempts.token()
    }

    @Synchronized private fun finish(attempt: Long) {
        // Check and state mutation must be indivisible relative to lock()/begin().
        if (attempts.accepts(attempt)) mutableState.compareAndSet(State.UNLOCKING, State.LOCKED)
    }

    suspend fun unlock(authenticate: suspend () -> Unit, openWallet: suspend () -> Unit,
                       discardStaleOpen: suspend () -> Unit = {}) {
        val attempt = begin() ?: return
        var opening = false
        var committed = false
        try {
            authenticate()
            currentCoroutineContext().ensureActive()
            attempts.check(attempt)
            opening = true
            openWallet()
            currentCoroutineContext().ensureActive()
            // A lifecycle stop may have locked the gate while authentication was visible.
            // Never let completion of that stale attempt reopen the UI.
            committed = attempts.withCurrent(attempt) { mutableState.compareAndSet(State.UNLOCKING, State.OPEN) }
        } finally {
            if (opening && !committed) discardStaleOpen()
            // Failure and coroutine cancellation both fail closed.
            finish(attempt)
        }
    }
}

object DeviceAuthSettings {
    /** Persist only after successful authentication; unavailable/cancelled auth must throw. */
    suspend fun update(
        currentlyRequired: Boolean,
        required: Boolean,
        authenticate: suspend () -> Unit,
        persist: (Boolean) -> Unit,
    ) {
        if (currentlyRequired != required) authenticate()
        currentCoroutineContext().ensureActive()
        persist(required)
    }
}
