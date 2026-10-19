package com.nexatrode.nexawal

import org.json.JSONObject

/** Untrusted public data, bounded independently of the wallet/history model. */
data class ExplorerAdvanced(
    val page: Int, val pageCount: Int, val version: Int, val coinbase: Boolean,
    val serializedSize: Long?, val downloadedSize: Long, val weight: Long?,
    val unlockKind: String, val unlockValue: String, val inputCount: Int, val outputCount: Int,
    val inputs: List<Input>, val outputs: List<Output>, val extra: String,
    val extraTruncated: Boolean, val receivedTimestamp: Long?,
) {
    data class Input(val index: Int, val keyImage: String, val ringSize: Int, val indices: List<String>, val truncated: Boolean)
    data class Output(val index: Int, val key: String, val amount: String?, val viewTag: Int?, val globalIndex: String?)
    companion object {
        fun unsigned(s: String) = s.matches(Regex("[0-9]{1,20}")) && s.toULongOrNull() != null
        private fun JSONObject.number(key: String): Long {
            val v = get(key); require(v is Int || v is Long)
            return (v as Number).toLong().also { require(it >= 0) }
        }
        private fun JSONObject.optionalNumber(key: String): Long? = if (isNull(key)) null else number(key)
        private fun JSONObject.optionalString(key: String): String? = if (isNull(key)) null else get(key) as String
        fun parse(j: JSONObject, requested: Int, now: Long, pending: Boolean): ExplorerAdvanced {
            val page = j.number("page").also { require(it == requested.toLong()) }.toInt()
            val ins = j.number("input_count").also { require(it in 1..4096) }.toInt()
            val outs = j.number("output_count").also { require(it in 1..4096) }.toInt()
            val pageCount = j.number("page_count").also { require(it == (maxOf(ins, outs) + 9L) / 10 && page < it) }.toInt()
            require(j.number("page_size") == 10L)
            val version = j.number("version").also { require(it == 1L || it == 2L) }.toInt()
            val coinbase = j.get("coinbase") as Boolean
            require(!coinbase || ins == 1)
            val inputRows = j.getJSONArray("inputs")
            val outputRows = j.getJSONArray("outputs")
            val start = page * 10
            require(inputRows.length() == minOf(10, ((if (coinbase) 0 else ins) - start).coerceAtLeast(0)))
            require(outputRows.length() == minOf(10, (outs - start).coerceAtLeast(0)))
            val inputs = List(inputRows.length()) { i ->
                val row = inputRows.getJSONObject(i)
                require(row.number("index") == (start + i).toLong())
                val key = (row.get("key_image") as String).also { require(ExplorerObservation.validId(it)) }
                val size = row.number("ring_size").also { require(it in 1..1_048_576) }.toInt()
                val truncated = row.get("indices_truncated") as Boolean
                val indices = row.getJSONArray("absolute_indices")
                require(indices.length() == minOf(size, 128) && truncated == (size > 128))
                val values = List(indices.length()) { n -> (indices.get(n) as String).also { require(unsigned(it)) } }
                require(values.zipWithNext().all { (a, b) -> a.toULong() < b.toULong() })
                Input(start + i, key, size, values, truncated)
            }
            val outputs = List(outputRows.length()) { i ->
                val row = outputRows.getJSONObject(i)
                require(row.number("index") == (start + i).toLong())
                val key = (row.get("key") as String).also { require(ExplorerObservation.validId(it)) }
                val amount = row.optionalString("public_amount_piconero")?.also { require(unsigned(it)) }
                require((amount == null) == (version == 2 && !coinbase))
                val tag = row.optionalNumber("view_tag")?.also { require(it <= 255) }?.toInt()
                val global = row.optionalString("global_index")?.also { require(unsigned(it)) }
                Output(start + i, key, amount, tag, global)
            }
            val downloaded = j.number("downloaded_size_bytes").also { require(it in 1..1_048_576) }
            val size = j.optionalNumber("serialized_size_bytes")?.also { require(it == downloaded) }
            val weight = j.optionalNumber("weight")?.also { require(size != null && it in 1..4_194_304) }
            val kind = j.get("unlock_kind") as String
            val unlock = (j.get("unlock_value") as String).also { require(unsigned(it)) }
            val n = unlock.toULong()
            require((kind == "none" && n == 0uL) || (kind == "height" && n > 0uL && n < 500_000_000uL) || (kind == "timestamp" && n >= 500_000_000uL))
            val extra = (j.get("extra_hex") as String).also { require(it.length <= 4096 && it.length % 2 == 0 && it.matches(Regex("[0-9a-f]*"))) }
            val truncated = j.get("extra_truncated") as Boolean
            require(!truncated || extra.length == 4096)
            val received = j.optionalNumber("received_timestamp")?.also { require(pending && it > 0 && it <= now + 120) }
            return ExplorerAdvanced(page, pageCount, version, coinbase, size, downloaded, weight, kind, unlock, ins, outs, inputs, outputs, extra, truncated, received)
        }
    }
}
