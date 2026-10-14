package com.nexatrode.nexawal.logic

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A confirmed backup/replace flow never decrypts the inaccessible old seed. */
object RecoveryReset {
    suspend fun run(authority: SessionAuthorityEpoch, token: Long,
        authenticate: suspend () -> Unit, reset: suspend () -> Unit,
        foreground: () -> Boolean, openSetup: suspend () -> Unit) {
        authenticate()
        currentCoroutineContext().ensureActive()
        authority.check(token)
        reset()
        currentCoroutineContext().ensureActive()
        if (foreground()) openSetup()
    }
}
