package com.nexatrode.nexawal

import android.content.Context
import android.content.SharedPreferences
import com.nexatrode.nexawal.logic.FiatEstimate
import com.nexatrode.nexawal.logic.FiatRate
import com.nexatrode.nexawal.logic.NetworkRouting
import java.util.Currency
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * MoneroConfig (Android)
 *
 * Android equivalent of iOS `MoneroConfig.swift` for the bring-up settings we care about right now:
 * - gap limit (subaddress minor lookahead within each account)
 * - account gap (account lookahead, i.e. number of major indices to scan starting from 0)
 * - basic network policy metadata (clearnet / i2p / hybrid) for UI parity
 *
 * Goals:
 * - Persist user overrides across app restarts (SharedPreferences)
 * - Provide wallet2-like defaults (match iOS defaults)
 * - Clamp values to safe ranges
 *
 * Notes:
 * - This file intentionally does NOT talk to walletcore/JNI directly.
 *   WalletManager (or the Settings UI) should:
 *   - read these values and apply them to walletcore (gap limit via API, account gap via env var)
 * - Network policy is currently UI-facing metadata only unless another caller explicitly uses it.
 */
object MoneroConfig {

    // SharedPreferences file name (scoped to app).
    private const val PREFS_NAME: String = "monero_config"

    // Keys (match iOS naming semantics where possible).
    private const val KEY_GAP_LIMIT: String = "monero_gap_limit"
    private const val KEY_ACCOUNT_GAP: String = "walletcore_account_gap"
    private const val KEY_REQUIRE_DEVICE_AUTH: String = "wallet_require_device_auth"
    private const val KEY_NETWORK_POLICY: String = "monero_network_policy"
    private const val KEY_I2P_RPC_ADDRESS: String = "monero_i2p_rpc_address"
    private const val KEY_I2P_HTTP_PROXY: String = "monero_i2p_http_proxy"
    private const val KEY_TECHNO_THEME: String = "ui_techno_theme"
    private const val KEY_CLASSIC_UI_LEGACY: String = "ui_classic_mode"
    private const val KEY_SYNC_DETAILS_EXPANDED: String = "ui_sync_details_expanded"
    private const val KEY_FIAT_ESTIMATES_ENABLED: String = "fiat_estimates_enabled"
    private const val KEY_FIAT_ESTIMATES_ENABLED_AT: String = "fiat_estimates_enabled_at_ms"
    private const val KEY_FIAT_CURRENCY: String = "fiat_currency"
    private const val KEY_FIAT_CURRENCY_INITIALIZED: String = "fiat_currency_initialized"
    private const val KEY_FIAT_RATE_CURRENCY: String = "fiat_rate_currency"
    private const val KEY_FIAT_RATE_PER_XMR: String = "fiat_rate_per_xmr"
    private const val KEY_FIAT_RATE_FETCHED_AT: String = "fiat_rate_fetched_at_ms"
    private const val KEY_FIAT_RATE_SOURCE: String = "fiat_rate_source"
    private const val KEY_ACCEPTED_TERMS_VERSION: String = "nexawal_accepted_terms_version"
    private const val KEY_SCAN_INTERRUPTED: String = "nexawal_scan_interrupted"
    private const val KEY_TRUSTED_SCANNED_HEIGHT: String = "nexawal_trusted_scanned_height"

    // Defaults (match iOS MoneroConfig.swift).
    const val DEFAULT_GAP_LIMIT: Int = 50
    const val DEFAULT_ACCOUNT_GAP: Int = 1
    const val DEFAULT_REQUIRE_DEVICE_AUTH: Boolean = false
    /** Techno Theme ON = neon terminal look; OFF (default) = standard look. */
    const val DEFAULT_TECHNO_THEME: Boolean = false
    private const val DEFAULT_NETWORK_POLICY_RAW: String = "clearnet"

    /** Bump when summary or full ToS changes so users must re-accept. */
    const val CURRENT_TERMS_VERSION: Int = 1
    const val TERMS_URL: String = "https://nexatrode.com/terms"
    // Safety clamps.
    private const val GAP_LIMIT_MIN: Int = 1
    private const val GAP_LIMIT_MAX: Int = 100_000
    private const val ACCOUNT_GAP_MIN: Int = 1
    private const val ACCOUNT_GAP_MAX: Int = 1_000

    private fun prefs(context: Context): SharedPreferences {
        // Use applicationContext to avoid leaking Activities.
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    enum class NetworkPolicy(val raw: String) {
        CLEARNET("clearnet"),
        I2P("i2p"),
        HYBRID("hybrid");

        companion object {
            fun fromRaw(raw: String?): NetworkPolicy =
                entries.firstOrNull { it.raw == raw } ?: CLEARNET
        }
    }

    /**
     * Read the persisted gap limit, falling back to default.
     *
     * Semantics:
     * - Represents the subaddress minor lookahead for each scanned account.
     * - Clamped to [1, 100_000].
     */
    @JvmStatic
    fun gapLimit(context: Context): Int {
        val raw = prefs(context).getInt(KEY_GAP_LIMIT, 0)
        val value = if (raw > 0) raw else DEFAULT_GAP_LIMIT
        return clamp(value, GAP_LIMIT_MIN, GAP_LIMIT_MAX)
    }

    /**
     * Persist a new gap limit.
     *
     * Input is clamped to [1, 100_000].
     */
    @JvmStatic
    fun setGapLimit(context: Context, gapLimit: Int) {
        val clamped = clamp(gapLimit, GAP_LIMIT_MIN, GAP_LIMIT_MAX)
        prefs(context).edit().putInt(KEY_GAP_LIMIT, clamped).apply()
    }

    /**
     * Read the persisted account gap (account lookahead), falling back to default.
     *
     * Semantics:
     * - Number of major indices to scan, starting from 0 (i.e. scan majors [0..accountGap)).
     * - Clamped to [1, 1_000].
     */
    @JvmStatic
    fun accountGap(context: Context): Int {
        val raw = prefs(context).getInt(KEY_ACCOUNT_GAP, 0)
        val value = if (raw > 0) raw else DEFAULT_ACCOUNT_GAP
        return clamp(value, ACCOUNT_GAP_MIN, ACCOUNT_GAP_MAX)
    }

    /**
     * Persist a new account gap (account lookahead).
     *
     * Input is clamped to [1, 1_000].
     */
    @JvmStatic
    fun setAccountGap(context: Context, accountGap: Int) {
        val clamped = clamp(accountGap, ACCOUNT_GAP_MIN, ACCOUNT_GAP_MAX)
        prefs(context).edit().putInt(KEY_ACCOUNT_GAP, clamped).apply()
    }

    @JvmStatic
    fun requireDeviceAuth(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_REQUIRE_DEVICE_AUTH, DEFAULT_REQUIRE_DEVICE_AUTH)
    }

    @JvmStatic
    fun setRequireDeviceAuth(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REQUIRE_DEVICE_AUTH, enabled).apply()
    }

    @JvmStatic
    fun isTechnoThemeEnabled(context: Context): Boolean {
        val prefs = prefs(context)
        if (prefs.contains(KEY_TECHNO_THEME)) {
            return prefs.getBoolean(KEY_TECHNO_THEME, DEFAULT_TECHNO_THEME)
        }
        // Migrate inverted legacy Classic UI preference (classic ON meant non-neon).
        if (prefs.contains(KEY_CLASSIC_UI_LEGACY)) {
            val techno = !prefs.getBoolean(KEY_CLASSIC_UI_LEGACY, false)
            prefs.edit()
                .putBoolean(KEY_TECHNO_THEME, techno)
                .remove(KEY_CLASSIC_UI_LEGACY)
                .apply()
            return techno
        }
        return DEFAULT_TECHNO_THEME
    }

    @JvmStatic
    fun setTechnoThemeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_TECHNO_THEME, enabled)
            .remove(KEY_CLASSIC_UI_LEGACY)
            .apply()
    }

    @JvmStatic
    fun syncDetailsExpanded(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SYNC_DETAILS_EXPANDED, false)
    }

    @JvmStatic
    fun setSyncDetailsExpanded(context: Context, expanded: Boolean) {
        prefs(context).edit().putBoolean(KEY_SYNC_DETAILS_EXPANDED, expanded).apply()
    }

    @JvmStatic
    fun fiatEstimatesEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_FIAT_ESTIMATES_ENABLED, false)
    }

    @JvmStatic
    fun fiatCurrencyInitialized(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_FIAT_CURRENCY_INITIALIZED, false)
    }

    @JvmStatic
    fun fiatCurrency(context: Context): String {
        val raw = prefs(context).getString(KEY_FIAT_CURRENCY, null).orEmpty()
        if (FiatEstimate.isSupported(raw)) return raw.uppercase()
        return FiatEstimate.hintedCurrency(localeCurrencyCode())
    }

    @JvmStatic
    fun setFiatCurrency(context: Context, code: String) {
        val normalized = if (FiatEstimate.isSupported(code)) code.uppercase() else "USD"
        prefs(context).edit()
            .putString(KEY_FIAT_CURRENCY, normalized)
            .putBoolean(KEY_FIAT_CURRENCY_INITIALIZED, true)
            .apply()
    }

    @JvmStatic
    fun fiatEstimatesEnabledAtMs(context: Context): Long {
        return prefs(context).getLong(KEY_FIAT_ESTIMATES_ENABLED_AT, 0L)
    }

    @JvmStatic
    fun setFiatEstimatesEnabled(context: Context, enabled: Boolean) {
        if (enabled && !fiatCurrencyInitialized(context)) {
            setFiatCurrency(context, FiatEstimate.hintedCurrency(localeCurrencyCode()))
        }
        val edit = prefs(context).edit().putBoolean(KEY_FIAT_ESTIMATES_ENABLED, enabled)
        if (enabled && fiatEstimatesEnabledAtMs(context) <= 0L) {
            edit.putLong(KEY_FIAT_ESTIMATES_ENABLED_AT, System.currentTimeMillis())
        }
        edit.apply()
    }

    @JvmStatic
    fun ensureFiatEstimatesEnabledAtMs(context: Context): Long {
        val stored = fiatEstimatesEnabledAtMs(context)
        if (stored > 0L) return stored
        if (!fiatEstimatesEnabled(context)) return 0L
        val now = System.currentTimeMillis()
        prefs(context).edit().putLong(KEY_FIAT_ESTIMATES_ENABLED_AT, now).apply()
        return now
    }

    @JvmStatic
    fun cachedFiatRate(context: Context): FiatRate? {
        val p = prefs(context)
        val currency = p.getString(KEY_FIAT_RATE_CURRENCY, null) ?: return null
        if (!FiatEstimate.isSupported(currency)) return null
        val raw = p.getString(KEY_FIAT_RATE_PER_XMR, null) ?: return null
        val perXmr = FiatEstimate.decimalOrNull(raw) ?: return null
        val fetchedAt = p.getLong(KEY_FIAT_RATE_FETCHED_AT, 0L)
        val source = p.getString(KEY_FIAT_RATE_SOURCE, "kraken") ?: "kraken"
        return FiatRate(currency, perXmr, fetchedAt, source)
    }

    @JvmStatic
    fun setCachedFiatRate(context: Context, rate: FiatRate?) {
        val edit = prefs(context).edit()
        if (rate == null) {
            edit.remove(KEY_FIAT_RATE_CURRENCY)
                .remove(KEY_FIAT_RATE_PER_XMR)
                .remove(KEY_FIAT_RATE_FETCHED_AT)
                .remove(KEY_FIAT_RATE_SOURCE)
        } else {
            edit.putString(KEY_FIAT_RATE_CURRENCY, rate.currency)
                .putString(KEY_FIAT_RATE_PER_XMR, rate.fiatPerXmr.stripTrailingZeros().toPlainString())
                .putLong(KEY_FIAT_RATE_FETCHED_AT, rate.fetchedAtMs)
                .putString(KEY_FIAT_RATE_SOURCE, rate.source)
        }
        edit.apply()
    }

    @JvmStatic
    fun networkPolicy(context: Context): NetworkPolicy {
        val raw = prefs(context).getString(KEY_NETWORK_POLICY, DEFAULT_NETWORK_POLICY_RAW)
        return NetworkPolicy.fromRaw(raw)
    }

    @JvmStatic
    fun setNetworkPolicy(context: Context, policy: NetworkPolicy) {
        prefs(context).edit().putString(KEY_NETWORK_POLICY, policy.raw).apply()
    }

    private val legacyDefaultI2pRpcAddresses: Set<String> = setOf(
        "cvxtgqjorfif6i5x5fenys6fj7hzddbgavpyutps6gphywnlklqa.b32.i2p:18081",
    )

    @JvmStatic
    fun i2pRpcAddress(context: Context): String {
        val saved = prefs(context).getString(KEY_I2P_RPC_ADDRESS, null)?.trim().orEmpty()
        if (saved.isEmpty()) return ""
        if (legacyDefaultI2pRpcAddresses.contains(saved.lowercase())) {
            prefs(context).edit().remove(KEY_I2P_RPC_ADDRESS).apply()
            return ""
        }
        return saved
    }

    @JvmStatic
    fun setI2pRpcAddress(context: Context, address: String?) {
        val edit = prefs(context).edit()
        if (address.isNullOrBlank()) {
            edit.remove(KEY_I2P_RPC_ADDRESS)
        } else {
            edit.putString(KEY_I2P_RPC_ADDRESS, address.trim())
        }
        edit.apply()
    }

    @JvmStatic
    fun i2pHttpProxyAddress(context: Context): String? {
        return prefs(context).getString(KEY_I2P_HTTP_PROXY, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    @JvmStatic
    fun setI2pHttpProxyAddress(context: Context, address: String?) {
        val edit = prefs(context).edit()
        if (address.isNullOrBlank()) {
            edit.remove(KEY_I2P_HTTP_PROXY)
        } else {
            edit.putString(KEY_I2P_HTTP_PROXY, address.trim())
        }
        edit.apply()
    }

    private fun toRoutingPolicy(policy: NetworkPolicy): NetworkRouting.Policy {
        return when (policy) {
            NetworkPolicy.CLEARNET -> NetworkRouting.Policy.CLEARNET
            NetworkPolicy.I2P -> NetworkRouting.Policy.I2P
            NetworkPolicy.HYBRID -> NetworkRouting.Policy.HYBRID
        }
    }

    @JvmStatic
    fun broadcastNodeUrl(context: Context, currentNodeUrl: String): String {
        return NetworkRouting.broadcastNodeUrl(
            policy = toRoutingPolicy(networkPolicy(context)),
            clearnetNodeUrl = currentNodeUrl,
            i2pRpcAddress = i2pRpcAddress(context),
        )
    }

    @JvmStatic
    fun scanNodeUrl(context: Context, currentNodeUrl: String): String {
        return NetworkRouting.scanNodeUrl(
            policy = toRoutingPolicy(networkPolicy(context)),
            clearnetNodeUrl = currentNodeUrl,
            i2pRpcAddress = i2pRpcAddress(context),
        )
    }

    /** True when daemon RPC for this policy should go through the I2P HTTP proxy. */
    @JvmStatic
    fun shouldUseI2pHttpProxy(context: Context, forBroadcast: Boolean): Boolean {
        return NetworkRouting.shouldUseI2pHttpProxy(
            policy = toRoutingPolicy(networkPolicy(context)),
            proxyConfigured = !i2pHttpProxyAddress(context).isNullOrBlank(),
            forBroadcast = forBroadcast,
        )
    }

    /**
     * Reset both values back to defaults (useful for debugging).
     */
    @JvmStatic
    fun resetToDefaults(context: Context) {
        prefs(context).edit()
            .putInt(KEY_GAP_LIMIT, DEFAULT_GAP_LIMIT)
            .putInt(KEY_ACCOUNT_GAP, DEFAULT_ACCOUNT_GAP)
            .putBoolean(KEY_REQUIRE_DEVICE_AUTH, DEFAULT_REQUIRE_DEVICE_AUTH)
            .putBoolean(KEY_TECHNO_THEME, DEFAULT_TECHNO_THEME)
            .remove(KEY_CLASSIC_UI_LEGACY)
            .putString(KEY_NETWORK_POLICY, DEFAULT_NETWORK_POLICY_RAW)
            .remove(KEY_I2P_RPC_ADDRESS)
            .remove(KEY_I2P_HTTP_PROXY)
            .apply()
    }

    /**
     * Convenience: dump current effective values for logging/diagnostics.
     */
    @JvmStatic
    fun snapshot(context: Context): Snapshot {
        return Snapshot(
            gapLimit = gapLimit(context),
            accountGap = accountGap(context),
            requireDeviceAuth = requireDeviceAuth(context),
            networkPolicy = networkPolicy(context),
            i2pRpcAddress = i2pRpcAddress(context),
            i2pHttpProxyAddress = i2pHttpProxyAddress(context),
        )
    }

    data class Snapshot(
        val gapLimit: Int,
        val accountGap: Int,
        val requireDeviceAuth: Boolean,
        val networkPolicy: NetworkPolicy,
        val i2pRpcAddress: String,
        val i2pHttpProxyAddress: String?,
    )

    // Terms acceptance
    @JvmStatic
    fun acceptedTermsVersion(context: Context): Int =
        prefs(context).getInt(KEY_ACCEPTED_TERMS_VERSION, 0)

    @JvmStatic
    fun needsTermsAcceptance(context: Context): Boolean =
        acceptedTermsVersion(context) < CURRENT_TERMS_VERSION

    @JvmStatic
    fun acceptCurrentTerms(context: Context) {
        prefs(context).edit().putInt(KEY_ACCEPTED_TERMS_VERSION, CURRENT_TERMS_VERSION).apply()
    }

    @JvmStatic
    fun scanInterrupted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCAN_INTERRUPTED, false)

    @JvmStatic
    fun setScanInterrupted(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SCAN_INTERRUPTED, value).apply()
    }

    @JvmStatic
    fun trustedScannedHeight(context: Context): Long =
        prefs(context).getLong(KEY_TRUSTED_SCANNED_HEIGHT, 0L)

    @JvmStatic
    fun setTrustedScannedHeight(context: Context, height: Long) {
        prefs(context).edit().putLong(KEY_TRUSTED_SCANNED_HEIGHT, height.coerceAtLeast(0L)).apply()
    }

    private fun clamp(v: Int, lo: Int, hi: Int): Int = max(lo, min(v, hi))

    private fun localeCurrencyCode(): String? {
        return runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()
    }

}
