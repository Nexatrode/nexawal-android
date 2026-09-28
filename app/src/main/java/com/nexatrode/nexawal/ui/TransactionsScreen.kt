package com.nexatrode.nexawal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexatrode.nexawal.R
import com.nexatrode.nexawal.*
import com.nexatrode.nexawal.logic.HistoryPageCache
import com.nexatrode.nexawal.logic.HistoryConfirmations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

internal class TransactionsPager(
    private val onRows: (List<Transfer>) -> Unit = {},
    private val fetch: suspend (String, HistoryQuery) -> HistoryPage = HistoryPaging::query,
) {
    val cache = HistoryPageCache<Transfer>()
    var count by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var version by mutableIntStateOf(0)
    var revision by mutableStateOf<String?>(null)
    var changed by mutableStateOf(false)
    var error by mutableStateOf<HistoryPageError?>(null)
    var loading by mutableStateOf(false)
    var anchorIndex: Int? = null; private set
    var generation = 0; private set
    private var query = HistoryQuery()
    private var walletId = ""
    private val inFlight = mutableSetOf<Int>()
    private val failed = mutableSetOf<Int>()
    fun cancel() { generation++; inFlight.clear(); loading = false }
    suspend fun reset(id: String, request: HistoryQuery, anchor: String? = null) {
        cancel(); walletId = id; query = request.copy(anchorTxid = anchor); anchorIndex = null; cache.clear(); failed.clear()
        count = 0; total = 0; revision = null; changed = false; error = null; version++
        load(0)
    }
    suspend fun load(index: Int) {
        val offset = index.coerceAtLeast(0) / 50 * 50
        if (cache.containsPage(offset)) { cache.touch(index); return }
        if (offset > 0 && revision == null) return
        if (changed || offset in failed || !inFlight.add(offset)) return
        val token = generation
        loading = true
        try {
            val request = query.copy(offset = offset, revision = revision)
            val page = fetch(walletId, request)
            if (token != generation) return
            if (revision != null && page.revision != revision) { changed = true; return }
            revision = page.revision; count = page.matchingCount; total = page.totalCount
            if (request.anchorTxid != null) { anchorIndex = page.anchorOffset ?: 0; query = query.copy(anchorTxid = null) }
            cache.insert(page.offset, page.transfers); version++; onRows(page.transfers)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            if (token != generation) return
            failed.add(offset)
            if (e.message?.contains("stale_history_cursor") == true) changed = true
            else error = HistoryPageError.LOAD
        } finally { if (token == generation) { inFlight.remove(offset); loading = inFlight.isNotEmpty() } }
    }
    suspend fun retry() {
        val offsets = failed.toList(); failed.clear(); error = null
        offsets.forEach { load(it) }
    }
}

internal enum class HistoryPageError { LOAD, DETAILS }

@Composable
internal fun TransactionsScreen(walletManager: WalletManager, palette: NexaPalette, initialFilter: String, onBack: () -> Unit) {
    val state by walletManager.state.collectAsState()
    val id = state.walletId ?: return
    val pager = remember(id, state.walletAddress, state.historySession) { TransactionsPager(onRows = { rows -> walletManager.fiatPrices.recordSeenTransfers(rows.map { FiatSeenTransfer(txid = it.txid, timestampSeconds = it.timestamp) }) }) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var from by rememberSaveable { mutableStateOf("") }
    var through by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Transfer?>(null) }
    val queryResult = remember(search, filter, from, through) {
        runCatching {
            val zone = ZoneId.systemDefault()
            val start = from.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).atStartOfDay(zone).toEpochSecond() }
            val end = through.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).plusDays(1).atStartOfDay(zone).toEpochSecond() - 1 }
            require(start == null || end == null || start <= end)
            HistoryQuery(filter = filter, search = search.trim(), fromTimestamp = start, toTimestamp = end)
        }
    }
    LaunchedEffect(id, state.walletAddress, state.historySession, queryResult) {
        pager.cancel(); selected = null
        delay(200)
        queryResult.getOrNull()?.let { pager.reset(id, it); listState.scrollToItem(0) }
    }
    LaunchedEffect(state.historyRevision) {
        if (pager.revision != null && state.historyRevision != null && pager.revision != state.historyRevision) pager.changed = true
    }
    DisposableEffect(pager) { onDispose { pager.cancel() } }
    Column(Modifier.fillMaxSize().background(palette.background).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.history_back)) }
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge, color = palette.primaryText, modifier = Modifier.padding(12.dp))
        }
        OutlinedTextField(search, { search = it }, label = { Text(stringResource(R.string.history_search_txid)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "all" to stringResource(R.string.history_filter_all),
                "received" to stringResource(R.string.direction_received),
                "sent" to stringResource(R.string.direction_sent),
                "pending" to stringResource(R.string.status_pending),
            ).forEach { (value, label) ->
                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(from, { from = it }, label = { Text(stringResource(R.string.history_from_date)) }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(through, { through = it }, label = { Text(stringResource(R.string.history_through_date)) }, singleLine = true, modifier = Modifier.weight(1f))
        }
        if (queryResult.isFailure) Text(stringResource(R.string.history_invalid_date_range), color = palette.danger)
        if (queryResult.isSuccess) {
            Text(stringResource(R.string.history_results_fmt, pager.count, pager.total), color = palette.secondaryText, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (state.refreshInProgress || state.balanceIsStaleWhileSyncing) Text(stringResource(R.string.history_incomplete_sync), color = palette.secondaryText)
        if (pager.changed) TextButton(onClick = { scope.launch { queryResult.getOrNull()?.let {
                val anchor = pager.cache.row(listState.firstVisibleItemIndex)?.txid
                val pixelOffset = listState.firstVisibleItemScrollOffset
                pager.reset(id, it, anchor)
                listState.scrollToItem(pager.anchorIndex ?: 0, if (pager.anchorIndex != null) pixelOffset else 0)
            } } }) { Text(stringResource(R.string.history_changed_reload)) }
        pager.error?.let {
            val message = when (it) {
                HistoryPageError.LOAD -> stringResource(R.string.history_load_failed)
                HistoryPageError.DETAILS -> stringResource(R.string.history_details_failed)
            }
            Text(message, color = palette.danger)
            if (it == HistoryPageError.LOAD) {
                TextButton(onClick = { scope.launch { pager.retry() } }) { Text(stringResource(R.string.history_retry)) }
            }
        }
        if (pager.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!pager.loading && pager.error == null && pager.count == 0) {
            Text(if (pager.total > 0) stringResource(R.string.history_no_matches) else stringResource(R.string.no_transactions_yet), color = palette.secondaryText)
        }
        TransactionHistoryList(pager, palette, listState, queryResult.isSuccess, Modifier.weight(1f), state.syncStatus?.chainHeight ?: 0) { row ->
            val session = state.historySession
            scope.launch {
                try {
                    val detail = HistoryPaging.query(id, HistoryQuery(limit = 1, txid = row.txid)).transfers.firstOrNull()
                    if (walletManager.state.value.walletId == id && walletManager.state.value.historySession == session) {
                        if (detail == null) pager.changed = true else selected = detail
                    }
                } catch (e: CancellationException) { throw e
                } catch (_: Exception) { if (walletManager.state.value.historySession == session) pager.error = HistoryPageError.DETAILS }
            }
        }
    }
    selected?.let { row -> TransferDetailsDialog(row.atChainHeight(state.syncStatus?.chainHeight ?: 0), walletManager.fiatPrices.snapshots.snapshot(row.txid)) { selected = null } }
}

internal fun Transfer.atChainHeight(chainHeight: Long): Transfer = copy(confirmations =
    HistoryConfirmations.count(height, isPending, chainHeight, confirmations))

@Composable
internal fun TransactionHistoryList(
    pager: TransactionsPager,
    palette: NexaPalette,
    listState: LazyListState,
    validQuery: Boolean = true,
    modifier: Modifier = Modifier,
    chainHeight: Long = 0,
    onSelect: (Transfer) -> Unit,
) {
    LaunchedEffect(listState, pager, pager.generation) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index }.distinct() }
            .collect { indices -> indices.map { it / 50 * 50 }.distinct().forEach { pager.load(it) } }
    }
    val version = pager.version
    LazyColumn(state = listState, modifier = modifier.testTag("transaction-history-list")) {
        items(if (validQuery) pager.count else 0, key = { index -> pager.cache.row(index)?.txid ?: "loading-$index" }) { index ->
            val row = remember(version, index, chainHeight) { pager.cache.row(index)?.atChainHeight(chainHeight) }
            if (row == null) {
                Text(
                    stringResource(if (pager.changed) R.string.history_reload else R.string.history_loading_transaction),
                    color = palette.secondaryText,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                )
            } else {
                TransferRow(row, palette) { onSelect(row) }
                HorizontalDivider(color = palette.separator)
            }
        }
    }
}
