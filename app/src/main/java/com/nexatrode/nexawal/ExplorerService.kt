package com.nexatrode.nexawal

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.net.Proxy
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ExplorerAccess {
    const val KEY = "explorer_details_enabled"
    fun preferences(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences("explorer_privacy", Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = preferences(context).getBoolean(KEY, false)
    fun setEnabled(context: Context, enabled: Boolean) { preferences(context).edit().putBoolean(KEY, enabled).apply() }
}

// Android's JSONTokener is recursive. Bound nesting before invoking it so an
// untrusted (but byte-bounded) explorer/error body cannot overflow the stack.
internal fun checkExplorerJson(text: String, limit: Int) {
    require(text.toByteArray(Charsets.UTF_8).size <= limit)
    var depth = 0; var quoted = false; var escaped = false
    for (c in text) {
        if (quoted) {
            if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
        } else when (c) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= 16) }
            '}', ']' -> { depth--; require(depth >= 0) }
        }
    }
    require(depth == 0 && !quoted)
}

data class ExplorerObservation(
    val txid: String, val status: String, val feePiconero: String?, val height: Long?,
    val blockHash: String?, val blockTimestamp: Long?, val confirmations: Long,
    val nodeReady: Boolean, val observedAt: Long,
    val doubleSpendSeen: Boolean = false, val details: ExplorerAdvanced? = null,
) {
    companion object {
        fun validId(id: String) = id.matches(Regex("[0-9a-f]{64}"))
        fun parse(body: String, txid: String, now: Long, page: Int? = null): ExplorerObservation {
            require(body.toByteArray(Charsets.UTF_8).size <= (if (page == null) 16_384 else 65_536) && validId(txid))
            checkExplorerJson(body, if (page == null) 16_384 else 65_536)
            val json = JSONObject(body)
            fun integer(key: String): Long {
                val v = json.get(key)
                require(v is Long || v is Int)
                return (v as Number).toLong().also { require(it >= 0) }
            }
            fun optionalInteger(key: String) = if (json.isNull(key)) null else integer(key)
            fun optionalString(key: String): String? = if (json.isNull(key)) null else (json.get(key) as String)
            require(integer("api_version") == 1L && json.get("network") == "mainnet" && json.get("txid") == txid)
            require(validId(json.get("tip_hash") as String))
            val chain = integer("chain_height").also { require(it > 0) }
            val observed = integer("observed_at").also { require(it > 0 && it <= now + 120 && it >= now - 300) }
            val fee = optionalString("fee_piconero")?.also {
                require(it.matches(Regex("[0-9]{1,20}")) && BigInteger(it) <= BigInteger("18446744073709551615"))
            }
            val height = optionalInteger("block_height")
            val hash = optionalString("block_hash")
            val time = optionalInteger("block_timestamp")
            val confirmations = integer("confirmations")
            val status = json.get("status") as String
            when (status) {
                "pending" -> require(height == null && hash == null && time == null && confirmations == 0L)
                "confirmed" -> require(height != null && height < chain && confirmations == chain - height && hash != null && validId(hash) && time != null && time > 0 && time <= now + 600)
                else -> error("invalid explorer status")
            }
            require((page == null) == !json.has("details"))
            val details = page?.let { ExplorerAdvanced.parse(json.getJSONObject("details"), it, now, status == "pending") }
            val conflict = if (json.has("double_spend_seen")) json.get("double_spend_seen") as Boolean else false
            return ExplorerObservation(txid, status, fee, height, hash, time, confirmations, json.get("node_ready") as Boolean, observed, conflict, details)
        }
    }
}

class ExplorerMissing : IOException()

private fun explorerHttpClient(): OkHttpClient = OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
    .followRedirects(false).followSslRedirects(false).cache(null)
    .connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
    .callTimeout(12, TimeUnit.SECONDS).build()

/** View-scoped, no persistent cache/timer; no wallet dependency or private data access. */
class ExplorerService(context: Context, private val client: OkHttpClient = explorerHttpClient()) : Closeable {
    private val preferences = ExplorerAccess.preferences(context)
    private val lock = Any()
    private val calls = mutableSetOf<Call>()
    private var closed = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ExplorerAccess.KEY && !preferences.getBoolean(key, false)) cancel()
    }
    init { preferences.registerOnSharedPreferenceChangeListener(listener) }
    fun cancel() = synchronized(lock) { calls.forEach { it.cancel() } }
    override fun close() {
        synchronized(lock) { closed = true; calls.forEach { it.cancel() } }
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        client.connectionPool.evictAll()
    }
    suspend fun fetch(txid: String, page: Int? = null): ExplorerObservation = suspendCancellableCoroutine { continuation ->
        require(ExplorerObservation.validId(txid) && (page == null || page in 0..409))
        val limit = if (page == null) 16_384 else 65_536
        val suffix = page?.let { "/details/$it" } ?: ""
        val call = client.newCall(Request.Builder().url("https://explorer.nexawal.com/api/v1/transactions/$txid$suffix").header("Accept", "application/json").get().build())
        continuation.invokeOnCancellation { call.cancel() }
        synchronized(lock) {
            if (closed || !preferences.getBoolean(ExplorerAccess.KEY, false) || !continuation.isActive) {
                continuation.cancel(); return@synchronized
            }
            calls.add(call)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    synchronized(lock) { calls.remove(call) }
                    if (continuation.isActive) continuation.resumeWithException(IOException("Explorer unavailable"))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            require(it.code in listOf(200, 404) && it.header("Content-Type")?.substringBefore(';') == "application/json")
                            val body = it.body ?: error("missing explorer body")
                            require(body.contentLength() <= limit)
                            val bytes = body.byteStream().use { input ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(1024)
                                while (true) {
                                    if (call.isCanceled()) throw IOException()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    require(output.size() + n <= limit)
                                    output.write(buffer, 0, n)
                                }
                                output.toByteArray()
                            }
                            val text = bytes.toString(Charsets.UTF_8)
                            checkExplorerJson(text,limit)
                            if (it.code == 404) {
                                val failure = JSONObject(text)
                                require(failure.get("api_version") == 1 && failure.getJSONObject("error").get("code") == "not_found")
                                if (page == null) throw ExplorerMissing() else throw IOException("Explorer unavailable")
                            }
                            ExplorerObservation.parse(text, txid, System.currentTimeMillis() / 1000, page)
                        }
                        if (continuation.isActive && preferences.getBoolean(ExplorerAccess.KEY, false) && !call.isCanceled()) continuation.resume(result)
                        else continuation.cancel()
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(if (e is ExplorerMissing) e else IOException("Explorer unavailable"))
                    } finally { synchronized(lock) { calls.remove(call) } }
                }
            })
        }
    }
}
