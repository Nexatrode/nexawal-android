package com.nexatrode.nexawal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Activity-scoped: both chooser state and an authorized copy survive rotation/lock. */
class HistoryExportCoordinator(private val copyScope: CoroutineScope? = null) : ViewModel() {
    data class Export(val file: File, val walletAddress: String, val uri: Uri)
    private var file: File? = null
    private var walletAddress = ""
    private var destination: Uri? = null
    private var copying = false
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed = _failed.asStateFlow()

    fun stage(source: File, address: String) {
        require(address.isNotBlank())
        check(!copying && file == null) { "An export is already pending" }
        file = source; walletAddress = address
    }
    fun selected(uri: Uri?) {
        if (copying) return
        if (uri == null) discard() else if (file != null) destination = uri
        _revision.value++
    }
    /** Start only for this unlocked wallet; UI disposal cannot cancel an authorized copy. */
    fun startIfReady(unlocked: Boolean, currentAddress: String?, write: suspend (Export) -> Unit): Job? {
        if (!unlocked || copying) return null
        val uri = destination ?: return null
        if (currentAddress != walletAddress) { discard(); return null }
        val source = file ?: return null
        val pending = Export(source, walletAddress, uri)
        file = null; destination = null; walletAddress = ""
        copying = true
        return (copyScope ?: viewModelScope).launch {
            try {
                write(pending)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _failed.value = true
            } finally {
                pending.file.delete()
                copying = false
                _revision.value++
            }
        }.also { job ->
            // A cleared scope may never enter the coroutine body.
            job.invokeOnCompletion { pending.file.delete() }
        }
    }
    fun acknowledgeFailure() { _failed.value = false }
    /** Never delete a file owned by an active copy. */
    fun discard() { file?.delete(); file = null; destination = null; walletAddress = "" }
    override fun onCleared() { discard() }
}
