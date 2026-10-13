package com.nexatrode.nexawal

import android.net.Uri
import androidx.lifecycle.ViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Activity-scoped: survives screen disposal, background lock and configuration changes. */
class HistoryExportCoordinator : ViewModel() {
    data class Export(val file: File, val walletAddress: String, val uri: Uri)
    private var file: File? = null
    private var walletAddress = ""
    private var destination: Uri? = null
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()

    fun stage(source: File, address: String) {
        require(address.isNotBlank())
        discard()
        file = source; walletAddress = address
    }
    fun selected(uri: Uri?) {
        if (uri == null) discard() else destination = uri
        _revision.value++
    }
    /** Only the unlocked activity may claim a completed chooser result. */
    fun claim(unlocked: Boolean, currentAddress: String?): Export? {
        if (!unlocked) return null
        val uri = destination ?: return null
        if (currentAddress != walletAddress) { discard(); return null }
        val source = file ?: return null
        file = null; destination = null
        return Export(source, walletAddress, uri)
    }
    fun discard() { file?.delete(); file = null; destination = null; walletAddress = "" }
    override fun onCleared() { discard() }
}
