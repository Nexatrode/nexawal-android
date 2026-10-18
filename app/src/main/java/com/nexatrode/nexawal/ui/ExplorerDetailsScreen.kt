package com.nexatrode.nexawal.ui

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexatrode.nexawal.*
import com.nexatrode.nexawal.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
internal fun ExplorerDetailsScreen(txid: String, mainnet: Boolean, lookup: ExplorerService? = null, onClose: () -> Unit) {
    val context = LocalContext.current
    val palette = rememberAppPalette()
    val preferences = remember { ExplorerAccess.preferences(context) }
    val service = remember { lookup ?: ExplorerService(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(ExplorerAccess.enabled(context)) }
    var requested by remember { mutableStateOf(ExplorerAccess.enabled(context)) }
    var request by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var data by remember(txid) { mutableStateOf<ExplorerObservation?>(null) }
    var error by remember(txid) { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    DisposableEffect(service) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ExplorerAccess.KEY) { requested = false; enabled = ExplorerAccess.enabled(context); data = null; error = null }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { requested = false; paused = true; data = null; error = null; service.cancel() }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        lifecycle.addObserver(observer)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener); lifecycle.removeObserver(observer); service.close() }
    }
    LaunchedEffect(txid, request, enabled, paused, mainnet, requested) {
        data = null; error = null; loading = false
        if (!enabled || !requested || !mainnet || paused || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@LaunchedEffect
        loading = true
        try {
            val result = service.fetch(txid)
            if (ExplorerAccess.enabled(context) && requested && !paused && mainnet && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) data = result
        }
        catch (e: CancellationException) { throw e }
        catch (e: ExplorerMissing) { if (requested && enabled && !paused && mainnet) error = R.string.explorer_missing }
        catch (e: Exception) { if (requested && enabled && !paused && mainnet) error = R.string.explorer_unavailable }
        finally { loading = false }
    }
    Surface(color = palette.background, contentColor = palette.primaryText, modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(fontFamily = if (palette.classic) FontFamily.Monospace else FontFamily.Default)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.explorer_details), style = MaterialTheme.typography.headlineSmall, fontFamily = if (palette.classic) FontFamily.Monospace else FontFamily.Default, color = palette.accent)
            Text(stringResource(R.string.explorer_reported))
            Text(stringResource(R.string.explorer_help), style = MaterialTheme.typography.bodySmall, color = palette.secondaryText)
            if (!enabled) Text(stringResource(R.string.explorer_disabled))
            if (!mainnet) Text(stringResource(R.string.explorer_mainnet))
            if (loading) CircularProgressIndicator(color = palette.accent)
            error?.let { Text(stringResource(it), color = palette.secondaryText) }
            data?.takeIf { enabled && !paused && mainnet }?.let { value ->
                Text(stringResource(R.string.tx_status_fmt, stringResource(if (value.status == "pending") R.string.status_pending else R.string.status_confirmed)))
                Text(stringResource(R.string.tx_confirmations_fmt, value.confirmations.toString()))
                Text(stringResource(R.string.tx_fee_fmt, value.feePiconero?.let { BigDecimal(it).movePointLeft(12).setScale(12).toPlainString() + " XMR" } ?: "—"))
                value.height?.let { Text(stringResource(R.string.tx_height_fmt, it.toString())) }
                value.blockHash?.let { SelectionContainer { Text(it, fontFamily = FontFamily.Monospace) } }
                value.blockTimestamp?.let { Text(stringResource(R.string.tx_time_fmt, TimeFormat.absolute(it) ?: "—")) }
                Text(stringResource(R.string.explorer_observed_fmt, TimeFormat.absolute(value.observedAt) ?: "—"))
                if (!value.nodeReady) Text(stringResource(R.string.explorer_node_stale), color = palette.secondaryText)
            }
            if (enabled && mainnet) Button(onClick = { requested = true; paused = false; request++ }, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.background)) { Text(stringResource(R.string.explorer_refresh)) }
            SelectionContainer { Text(txid, fontFamily = FontFamily.Monospace) }
            val clipboard = ClipboardCompat.current()
            val scope = rememberCoroutineScope()
            TextButton(onClick = { scope.launch { ClipboardCompat.setText(clipboard, "https://explorer.nexawal.com/tx/$txid") } }) { Text(stringResource(R.string.copy_explorer_link), color = palette.accent) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_close), color = palette.accent) }
        }
        }
    }
}
