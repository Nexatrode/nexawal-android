package com.nexatrode.nexawal

import android.content.Context
import com.nexatrode.nexawal.logic.FiatEstimate
import com.nexatrode.nexawal.logic.FiatRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy
import java.util.concurrent.TimeUnit

class FiatPriceService(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fetchMutex = Mutex()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val _displayRate = MutableStateFlow<FiatRate?>(null)
    val displayRate: StateFlow<FiatRate?> = _displayRate.asStateFlow()

    val snapshots = FiatSnapshotStore(appContext)
    private var loopJob: Job? = null
    private var staleJob: Job? = null

    init {
        republishFromCache()
    }

    fun onForeground() {
        republishFromCache()
        scope.launch { refreshIfNeeded(force = false) }
        startLoop()
    }

    fun settingsDidChange() {
        republishFromCache()
        if (!canFetch()) {
            publish(null)
            stopLoop()
            return
        }
        MoneroConfig.ensureFiatEstimatesEnabledAtMs(appContext)
        scope.launch { refreshIfNeeded(force = true) }
        startLoop()
    }

    fun canFetch(): Boolean = MoneroConfig.fiatEstimatesEnabled(appContext)

    fun recordSend(txid: String) {
        snapshots.record(txid, _displayRate.value, kind = "send")
    }

    fun recordSeenTransfers(transfers: Collection<FiatSeenTransfer>) {
        snapshots.recordNewTransfers(
            transfers = transfers,
            rate = _displayRate.value,
            optedInAtMs = MoneroConfig.ensureFiatEstimatesEnabledAtMs(appContext),
        )
    }

    private fun republishFromCache() {
        if (!canFetch()) {
            publish(null)
            return
        }
        val now = System.currentTimeMillis()
        val currency = MoneroConfig.fiatCurrency(appContext)
        val cached = MoneroConfig.cachedFiatRate(appContext)
        publish(
            cached?.takeIf { it.currency == currency && FiatEstimate.isFresh(it.fetchedAtMs, now) },
        )
    }

    private fun startLoop() {
        stopLoop()
        if (!canFetch()) return
        loopJob = scope.launch {
            while (isActive) {
                delay(FiatEstimate.REFRESH_INTERVAL_MS)
                refreshIfNeeded(force = false)
            }
        }
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
        staleJob?.cancel()
        staleJob = null
    }

    suspend fun refreshIfNeeded(force: Boolean) {
        if (!canFetch()) {
            publish(null)
            return
        }
        val currency = MoneroConfig.fiatCurrency(appContext)
        val now = System.currentTimeMillis()
        val current = _displayRate.value
        if (
            !force &&
            current != null &&
            current.currency == currency &&
            FiatEstimate.isFresh(current.fetchedAtMs, now) &&
            now - current.fetchedAtMs < FiatEstimate.REFRESH_INTERVAL_MS
        ) {
            return
        }
        fetchMutex.withLock {
            val lockedCurrency = MoneroConfig.fiatCurrency(appContext)
            runCatching { fetchRate(lockedCurrency) }
                .onSuccess { rate ->
                    MoneroConfig.setCachedFiatRate(appContext, rate)
                    if (canFetch() && MoneroConfig.fiatCurrency(appContext) == lockedCurrency) {
                        publish(FiatEstimate.liveRate(rate, System.currentTimeMillis()))
                    }
                }
                .onFailure { republishFromCache() }
        }
    }

    private fun publish(rate: FiatRate?) {
        _displayRate.value = FiatEstimate.liveRate(rate, System.currentTimeMillis())
        scheduleStaleHide()
    }

    private fun scheduleStaleHide() {
        staleJob?.cancel()
        val rate = _displayRate.value ?: return
        val remaining = FiatEstimate.msUntilStale(rate.fetchedAtMs, System.currentTimeMillis())
        if (remaining <= 0L) {
            _displayRate.value = null
            return
        }
        staleJob = scope.launch {
            delay(remaining)
            _displayRate.value = FiatEstimate.liveRate(_displayRate.value, System.currentTimeMillis())
        }
    }

    private suspend fun fetchRate(currency: String): FiatRate = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (currency == "EUR") {
            val last = fetchKrakenLastTrade("XMREUR")
            return@withContext FiatRate("EUR", last, now, "kraken")
        }
        val usd = fetchKrakenLastTrade("XMRUSD")
        if (currency == "USD") {
            return@withContext FiatRate("USD", usd, now, "kraken")
        }
        val fx = fetchFrankfurter(currency)
        FiatRate(currency, FiatEstimate.combine(usd, fx), now, "kraken+frankfurter")
    }

    private fun fetchKrakenLastTrade(pair: String): java.math.BigDecimal {
        val json = get("https://api.kraken.com/0/public/Ticker?pair=$pair")
        return FiatEstimate.parseKrakenLastTrade(json) ?: error("kraken parse failed")
    }

    private fun fetchFrankfurter(symbol: String): java.math.BigDecimal {
        val json = get("https://api.frankfurter.dev/v1/latest?base=USD&symbols=$symbol")
        return FiatEstimate.parseFrankfurterRate(json, symbol) ?: error("frankfurter parse failed")
    }

    private fun get(url: String): String {
        val response = client.newCall(Request.Builder().url(url).get().build()).execute()
        response.use {
            if (!it.isSuccessful) error("http ${it.code}")
            return it.body?.string().orEmpty()
        }
    }
}
