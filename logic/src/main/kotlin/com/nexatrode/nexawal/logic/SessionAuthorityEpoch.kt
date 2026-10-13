package com.nexatrode.nexawal.logic

import kotlinx.coroutines.CancellationException

/** Linearizes native key restoration against a lifecycle lock, including before UI publication. */
class SessionAuthorityEpoch {
    private var generation = 0L
    @Synchronized fun token(): Long = generation
    @Synchronized fun invalidate(clearAuthority: () -> Unit = {}) { generation++; clearAuthority() }
    @Synchronized fun accepts(token: Long): Boolean = token == generation
    @Synchronized fun check(token: Long) {
        if (token != generation) throw CancellationException("Wallet session was locked")
    }
    @Synchronized fun <T> withCurrent(token: Long, operation: () -> T): T {
        check(token)
        return operation()
    }
}
