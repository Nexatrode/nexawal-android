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
    val state: StateFlow<State> = mutableState.asStateFlow()

    fun lock() {
        mutableState.value = State.LOCKED
    }

    suspend fun unlock(authenticate: suspend () -> Unit, openWallet: suspend () -> Unit) {
        if (!mutableState.compareAndSet(State.LOCKED, State.UNLOCKING)) return
        try {
            authenticate()
            currentCoroutineContext().ensureActive()
            openWallet()
            currentCoroutineContext().ensureActive()
            // A lifecycle stop may have locked the gate while authentication was visible.
            // Never let completion of that stale attempt reopen the UI.
            mutableState.compareAndSet(State.UNLOCKING, State.OPEN)
        } finally {
            // Failure and coroutine cancellation both fail closed.
            mutableState.compareAndSet(State.UNLOCKING, State.LOCKED)
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
