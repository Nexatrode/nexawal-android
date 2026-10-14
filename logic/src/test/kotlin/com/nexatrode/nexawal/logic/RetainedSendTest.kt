package com.nexatrode.nexawal.logic

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RetainedSendTest {
    @Test fun rotationCancelsOnlyTheWaiterAndCannotStartAnotherPayment() = runBlocking {
        val ownerJob = SupervisorJob()
        val owner = CoroutineScope(coroutineContext + ownerJob)
        val retained = RetainedSend(owner)
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Throwable?>()
        var journals = 0; var relays = 0
        val ui = launch {
            retained.run(onBegin = {}, onFinish = { completed.complete(it) }) {
                started.complete(Unit); resume.await()
                journals++; relays++; "fixture-txid"
            }
        }
        started.await()
        ui.cancelAndJoin()
        try {
            retained.run({}, {}) { relays++; "must not run" }
            fail("duplicate payment accepted")
        } catch (error: IllegalStateException) {
            assertEquals(SendGate.ALREADY_IN_PROGRESS, error.message)
        }
        resume.complete(Unit)
        assertNull(completed.await())
        assertEquals(1, journals); assertEquals(1, relays)
        assertEquals("next", retained.run({}, {}) { "next" })
        ownerJob.cancel()
    }
    @Test fun failureAfterUiDisposalIsStillPublishedAndJournalIsPreserved() = runBlocking {
        val ownerJob = SupervisorJob()
        val retained = RetainedSend(CoroutineScope(coroutineContext + ownerJob))
        val prepared = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val failure = CompletableDeferred<Throwable?>()
        var journal = false
        val ui = launch {
            retained.run({}, { failure.complete(it) }) {
                journal = true; prepared.complete(Unit); resume.await()
                error("fixture relay unavailable")
            }
        }
        prepared.await(); ui.cancelAndJoin(); resume.complete(Unit)
        assertEquals("fixture relay unavailable", failure.await()?.message)
        assertTrue(journal)
        ownerJob.cancel()
    }
    @Test fun cancelledAuthorizationCannotStartAWorker() = runBlocking {
        val retained = RetainedSend(this)
        var started = false
        val ui = launch(start = CoroutineStart.LAZY) {
            retained.run({ started = true }, {}) { started = true }
        }
        ui.cancel(); ui.join()
        assertFalse(started)
    }
    @Test fun closedOwnerRefusesWorkAndReleasesTheGate() = runBlocking {
        val ownerJob = SupervisorJob(); ownerJob.cancel()
        val retained = RetainedSend(CoroutineScope(coroutineContext + ownerJob))
        var operations = 0; var finishes = 0
        repeat(2) {
            try { retained.run({}, { finishes++ }) { operations++ } }
            catch (_: CancellationException) {}
        }
        assertEquals(0, operations); assertEquals(2, finishes)
    }
}
