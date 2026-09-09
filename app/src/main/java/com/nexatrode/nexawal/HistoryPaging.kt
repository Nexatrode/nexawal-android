package com.nexatrode.nexawal

import com.nexatrode.nexawal.walletcore.WalletCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class HistoryQuery(
    val limit: Int = 50, val offset: Int = 0, val revision: String? = null,
    val filter: String = "all", val search: String = "",
    @SerialName("from_timestamp") val fromTimestamp: Long? = null,
    @SerialName("to_timestamp") val toTimestamp: Long? = null,
    val txid: String? = null,
    @SerialName("anchor_txid") val anchorTxid: String? = null,
)

@Serializable
data class HistoryPage(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("wallet_id") val walletId: String,
    val revision: String,
    @SerialName("total_count") val totalCount: Int,
    @SerialName("matching_count") val matchingCount: Int,
    @SerialName("pending_count") val pendingCount: Int,
    val offset: Int,
    @SerialName("next_offset") val nextOffset: Int? = null,
    @SerialName("anchor_offset") val anchorOffset: Int? = null,
    @SerialName("last_scanned_height") val lastScannedHeight: Long,
    @SerialName("chain_height") val chainHeight: Long,
    @SerialName("chain_time") val chainTime: Long,
    val transfers: List<Transfer>,
)

object HistoryPaging {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun decode(raw: String, walletId: String): HistoryPage {
        val page = json.decodeFromString<HistoryPage>(raw)
        require(page.schemaVersion == 1 && page.walletId == walletId) { "Invalid or wrong-wallet history page" }
        require(page.revision.isNotBlank())
        require(page.matchingCount in 0..page.totalCount && page.pendingCount in 0..page.totalCount)
        require(page.offset >= 0 && page.transfers.size <= 200 && page.transfers.size <= (page.matchingCount - page.offset).coerceAtLeast(0))
        require(page.nextOffset == null || (page.nextOffset.toLong() == page.offset.toLong() + page.transfers.size && page.nextOffset < page.matchingCount && page.transfers.isNotEmpty()))
        require(page.anchorOffset == null || (page.anchorOffset >= page.offset && page.anchorOffset.toLong() < page.offset.toLong() + page.transfers.size))
        require(page.transfers.map { it.txid }.distinct().size == page.transfers.size)
        page.transfers.forEach {
            require(it.txid.isNotBlank() && it.direction in setOf("in", "out", "self") && it.amount >= 0)
            require(it.fee == null || it.fee >= 0)
        }
        return page
    }
    suspend fun query(walletId: String, query: HistoryQuery = HistoryQuery()): HistoryPage = withContext(Dispatchers.IO) {
        decode(WalletCore.queryTransfersJson(walletId, json.encodeToString(query)), walletId)
    }
}
