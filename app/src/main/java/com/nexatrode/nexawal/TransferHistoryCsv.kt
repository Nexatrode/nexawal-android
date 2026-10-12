package com.nexatrode.nexawal

import kotlinx.coroutines.CancellationException
import java.io.Writer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object TransferHistoryCsv {
    const val HEADER = "direction,amount_xmr,amount_piconero,fee_xmr,fee_piconero,block,timestamp_utc,confirmations,status,txid\n"
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT)
        .withZone(ZoneOffset.UTC)

    suspend fun writeAll(
        walletId: String,
        query: HistoryQuery,
        writer: Writer,
        fetch: suspend (String, HistoryQuery) -> HistoryPage = HistoryPaging::query,
        shouldContinue: () -> Boolean = { true },
    ): Int {
        writer.append(HEADER)
        var request = query.copy(limit = 200, offset = 0, revision = null, anchorTxid = null)
        var expectedRevision: String? = null
        var count = 0
        while (true) {
            if (!shouldContinue()) throw CancellationException("History export was cancelled")
            val page = fetch(walletId, request)
            if (!shouldContinue()) throw CancellationException("History export was cancelled")
            if (expectedRevision != null && expectedRevision != page.revision) {
                throw IllegalStateException("History changed during export; reload and try again")
            }
            expectedRevision = page.revision
            page.transfers.forEach { row ->
                writer.append(row(row)).append('\n')
                count++
            }
            writer.flush()
            val nextOffset = page.nextOffset ?: break
            request = request.copy(offset = nextOffset, revision = page.revision)
        }
        return count
    }

    internal fun row(transfer: Transfer): String {
        val status = if (transfer.pending) "pending" else "confirmed"
        val timestamp = transfer.timestamp?.let {
            timestampFormat.format(Instant.ofEpochSecond(it))
        }.orEmpty()
        val values = listOf(
            transfer.direction,
            transfer.amountXmr(),
            transfer.amount.toString(),
            transfer.feeXmr().orEmpty(),
            transfer.fee?.toString().orEmpty(),
            transfer.height?.toString().orEmpty(),
            timestamp,
            transfer.confirmations.toString(),
            status,
            transfer.txid,
        )
        return values.joinToString(",", transform = ::escape)
    }

    internal fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
}
