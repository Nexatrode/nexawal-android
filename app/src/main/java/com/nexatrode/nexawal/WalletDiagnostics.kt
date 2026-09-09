package com.nexatrode.nexawal

/** Never log wallet data in release builds; debug diagnostics also require explicit opt-in. */
object WalletDiagnostics {
    private val enabled: Boolean = BuildConfig.DEBUG && System.getenv("NEXAWAL_DIAGNOSTICS") == "1"
    fun i(tag: String, message: String, error: Throwable? = null) {
        if (enabled) android.util.Log.i(tag, message, error)
    }
    fun w(tag: String, message: String, error: Throwable? = null) {
        if (enabled) android.util.Log.w(tag, message, error)
    }
    fun e(tag: String, message: String, error: Throwable? = null) {
        if (enabled) android.util.Log.e(tag, message, error)
    }
    fun d(tag: String, message: String, error: Throwable? = null) {
        if (enabled) android.util.Log.d(tag, message, error)
    }
}
