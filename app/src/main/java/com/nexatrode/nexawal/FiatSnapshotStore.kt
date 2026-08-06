package com.nexatrode.nexawal

import android.content.Context
import com.nexatrode.nexawal.logic.FiatEstimate
import com.nexatrode.nexawal.logic.FiatRate
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class FiatTxSnapshot(
    val currency: String,
    val fiatPerXmr: String,
    val recordedAtMs: Long,
    val kind: String,
)

data class FiatSeenTransfer(
    val txid: String,
    val timestampSeconds: Long?,
)

class FiatSnapshotStore(context: Context) {
    private val file: File = File(File(context.applicationContext.filesDir, "WalletSlot"), "tx_fiat_snapshots.json")

    @Synchronized
    fun snapshot(txid: String): FiatTxSnapshot? = load().snapshots[txid]

    @Synchronized
    fun record(txid: String, rate: FiatRate?, kind: String, nowMs: Long = System.currentTimeMillis()) {
        val trimmed = txid.trim()
        if (trimmed.isEmpty()) return
        val state = load()
        val observed = state.observed.toMutableSet()
        observed.add(trimmed)
        val snapshots = state.snapshots.toMutableMap()
        if (rate != null && FiatEstimate.isFresh(rate.fetchedAtMs, nowMs) && !snapshots.containsKey(trimmed)) {
            snapshots[trimmed] = FiatTxSnapshot(
                currency = rate.currency,
                fiatPerXmr = rate.fiatPerXmr.stripTrailingZeros().toPlainString(),
                recordedAtMs = nowMs,
                kind = kind,
            )
        }
        save(StoreState(observed, snapshots))
    }

    @Synchronized
    fun recordNewTransfers(
        transfers: Collection<FiatSeenTransfer>,
        rate: FiatRate?,
        optedInAtMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val state = load()
        val observed = state.observed.toMutableSet()
        val snapshots = state.snapshots.toMutableMap()
        var changed = false

        for (item in transfers) {
            val trimmed = item.txid.trim()
            if (trimmed.isEmpty()) continue
            if (!observed.add(trimmed)) continue
            changed = true
            if (!FiatEstimate.shouldRecordSeenSnapshot(item.timestampSeconds, optedInAtMs)) continue
            if (snapshots.containsKey(trimmed)) continue
            if (rate == null || !FiatEstimate.isFresh(rate.fetchedAtMs, nowMs)) continue
            snapshots[trimmed] = FiatTxSnapshot(
                currency = rate.currency,
                fiatPerXmr = rate.fiatPerXmr.stripTrailingZeros().toPlainString(),
                recordedAtMs = nowMs,
                kind = "seen",
            )
        }

        if (changed) save(StoreState(observed, snapshots))
    }

    private data class StoreState(
        val observed: Set<String>,
        val snapshots: Map<String, FiatTxSnapshot>,
    )

    private fun load(): StoreState {
        if (!file.exists()) return StoreState(emptySet(), emptyMap())
        val raw = runCatching { file.readText() }.getOrNull().orEmpty()
        if (raw.isBlank()) return StoreState(emptySet(), emptyMap())
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return StoreState(emptySet(), emptyMap())
        val snapshotObject = root.optJSONObject("snapshots")
        return if (snapshotObject != null || root.has("observed")) {
            StoreState(
                observed = jsonStringSet(root.optJSONArray("observed")) + snapshotKeys(snapshotObject),
                snapshots = parseSnapshots(snapshotObject ?: JSONObject()),
            )
        } else {
            val snapshots = parseSnapshots(root)
            StoreState(observed = snapshots.keys, snapshots = snapshots)
        }
    }

    private fun save(state: StoreState) {
        file.parentFile?.mkdirs()
        val snapshotsObj = JSONObject()
        state.snapshots.forEach { (txid, snap) ->
            snapshotsObj.put(
                txid,
                JSONObject()
                    .put("currency", snap.currency)
                    .put("fiatPerXmr", snap.fiatPerXmr)
                    .put("recordedAtMs", snap.recordedAtMs)
                    .put("kind", snap.kind),
            )
        }
        val observedArr = JSONArray()
        state.observed.forEach { observedArr.put(it) }
        file.writeText(
            JSONObject()
                .put("observed", observedArr)
                .put("snapshots", snapshotsObj)
                .toString(),
        )
    }

    private fun parseSnapshots(root: JSONObject): Map<String, FiatTxSnapshot> {
        val out = linkedMapOf<String, FiatTxSnapshot>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val txid = keys.next()
            val obj = root.optJSONObject(txid) ?: continue
            val currency = obj.optString("currency")
            val fiatPerXmr = obj.optString("fiatPerXmr")
            if (currency.isBlank() || fiatPerXmr.isBlank()) continue
            out[txid] = FiatTxSnapshot(
                currency = currency,
                fiatPerXmr = fiatPerXmr,
                recordedAtMs = obj.optLong("recordedAtMs"),
                kind = obj.optString("kind", "seen"),
            )
        }
        return out
    }

    private fun jsonStringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        val out = linkedSetOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i).trim()
            if (value.isNotEmpty()) out.add(value)
        }
        return out
    }

    private fun snapshotKeys(obj: JSONObject?): Set<String> {
        if (obj == null) return emptySet()
        val out = linkedSetOf<String>()
        val keys = obj.keys()
        while (keys.hasNext()) out.add(keys.next())
        return out
    }
}
