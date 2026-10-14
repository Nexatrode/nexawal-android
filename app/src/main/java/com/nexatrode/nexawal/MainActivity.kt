package com.nexatrode.nexawal

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexatrode.nexawal.logic.WalletAccessGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.nexatrode.nexawal.ui.AppScaffold
import com.nexatrode.nexawal.ui.TermsAcceptanceScreen
import com.nexatrode.nexawal.ui.WalletCreationScreen
import com.nexatrode.nexawal.ui.SecretTextInput
import com.nexatrode.nexawal.ui.theme.NexawalTheme

class MainActivity : ComponentActivity() {
    private lateinit var walletManager: WalletManager
    private lateinit var historyExports: HistoryExportCoordinator
    private lateinit var historyExportLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private val accessGate = WalletAccessGate()
    private val sessionHandler = Handler(Looper.getMainLooper())
    private val idleLockAction = Runnable { lockProtectedSession(closeCore = true) }

    private fun lockProtectedSession(closeCore: Boolean = false) {
        sessionHandler.removeCallbacks(idleLockAction)
        if (!::walletManager.isInitialized) return
        if (MoneroConfig.requireDeviceAuth(applicationContext)) accessGate.lock()
        if (closeCore) walletManager.closeProtectedSession()
        else walletManager.sealSessionForBackground()
    }

    private fun scheduleIdleLock() {
        sessionHandler.removeCallbacks(idleLockAction)
        if (::walletManager.isInitialized &&
            accessGate.state.value == WalletAccessGate.State.OPEN &&
            MoneroConfig.requireDeviceAuth(applicationContext) &&
            walletManager.state.value.walletId != null
        ) {
            sessionHandler.postDelayed(idleLockAction, PROTECTED_IDLE_TIMEOUT_MS)
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        scheduleIdleLock()
    }

    override fun onDestroy() {
        // Configuration changes retain the manager/send worker, not this Activity's timer.
        sessionHandler.removeCallbacks(idleLockAction)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Seeds, balances and history must never enter screenshots, casting or Recents snapshots.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        walletManager = (application as NexaWalApp).walletManager
        historyExports = androidx.lifecycle.ViewModelProvider(this)[HistoryExportCoordinator::class.java]
        historyExportLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            historyExports.selected(uri)
        }

        enableEdgeToEdge()
        setContent {
            NexawalTheme {
                var termsAccepted by remember {
                    mutableStateOf(!MoneroConfig.needsTermsAcceptance(applicationContext))
                }

                if (!termsAccepted) {
                    TermsAcceptanceScreen(
                        onAccepted = { termsAccepted = true },
                    )
                    return@NexawalTheme
                }

                val state by walletManager.state.collectAsState()
                val access by accessGate.state.collectAsState()
                val exportRevision by historyExports.revision.collectAsState()
                val exportFailed by historyExports.failed.collectAsState()
                LaunchedEffect(access, state.walletAddress, exportRevision) {
                    // The worker retains the application resolver, not this Activity.
                    val resolver = applicationContext.contentResolver
                    historyExports.startIfReady(access == WalletAccessGate.State.OPEN, state.walletAddress) { pending ->
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val output = resolver.openOutputStream(pending.uri)
                                ?: error("Could not open export destination")
                            output.use { destination ->
                                pending.file.inputStream().use { input -> input.copyTo(destination) }
                            }
                        }
                    }
                }
                LaunchedEffect(exportFailed) {
                    if (exportFailed) {
                        android.widget.Toast.makeText(this@MainActivity, R.string.history_export_failed, android.widget.Toast.LENGTH_LONG).show()
                        historyExports.acknowledgeFailure()
                    }
                }
                val scope = rememberCoroutineScope()
                var unlockError by remember { mutableStateOf<String?>(null) }
                var showRecoveryReset by remember { mutableStateOf(false) }
                var resettingWallet by remember { mutableStateOf(false) }

                SyncLifecycleEffects(
                    walletManager = walletManager,
                    refreshInProgress = state.refreshInProgress,
                    lockProtectedSession = { lockProtectedSession(closeCore = false) },
                    changingConfigurations = { this@MainActivity.isChangingConfigurations },
                )

                suspend fun unlockWallet() {
                    unlockError = null
                    val sessionToken = walletManager.sessionAuthority.token()
                    try {
                        accessGate.unlock(authenticate = {
                            walletManager.loadVersion()
                            walletManager.loadSettingsOnLaunch()
                            walletManager.fiatPrices.onForeground()

                            val hasStoredWallet = walletManager.hasStoredWallet() ||
                                walletManager.state.value.walletId != null
                            if (hasStoredWallet && MoneroConfig.requireDeviceAuth(applicationContext)) {
                                MnemonicCipher.prepareDeviceAuthKey()
                                DeviceAuthGate.authenticate(
                                    activity = this@MainActivity,
                                    title = getString(R.string.biometric_unlock_wallet),
                                    subtitle = getString(R.string.biometric_unlock_subtitle)
                                )
                            }
                        }, openWallet = {
                            walletManager.awaitSessionCleanup()
                            // A retained manager may be syncing. Do not reopen/reset it on rotation.
                            if (walletManager.state.value.walletId == null && walletManager.hasStoredWallet()) {
                                val loaded = walletManager.loadStoredWalletOnLaunch(sessionToken)
                                check(loaded) { getString(R.string.authentication_failed) }
                                walletManager.refreshWalletInBackground()
                            } else if (walletManager.state.value.walletId != null && walletManager.hasStoredWallet()) {
                                check(walletManager.unlockStoredMetadataSession(sessionToken)) {
                                    getString(R.string.authentication_failed)
                                }
                            }
                        }, discardStaleOpen = { walletManager.discardStaleSession(sessionToken) })
                        scheduleIdleLock()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        unlockError = getString(R.string.authentication_failed)
                    }
                }

                LaunchedEffect(Unit) { unlockWallet() }
                LaunchedEffect(access, state.walletId) { scheduleIdleLock() }

                // iOS parity:
                // - If a wallet is open, show the main tab UI.
                // - Otherwise, show the wallet creation/import flow (seed paste view).
                if (access != WalletAccessGate.State.OPEN) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.unlock_your_wallet))
                        unlockError?.let { Text(it) }
                        Button(
                            enabled = access == WalletAccessGate.State.LOCKED,
                            onClick = { scope.launch { unlockWallet() } },
                        ) {
                            Text(stringResource(if (access == WalletAccessGate.State.UNLOCKING)
                                R.string.unlocking_ellipsis else R.string.unlock_existing_wallet))
                        }
                        Button(enabled = access == WalletAccessGate.State.LOCKED && !resettingWallet && !state.sendInProgress,
                            onClick = { showRecoveryReset = true }) {
                            Text(stringResource(R.string.replace_wallet_title))
                        }
                    }
                } else if (state.walletId != null) {
                    AppScaffold(walletManager = walletManager, onHistoryExportReady = { file, address ->
                        var staged = false
                        try {
                            historyExports.stage(file, address)
                            staged = true
                            historyExportLauncher.launch("nexawal-history.csv")
                        } catch (_: Exception) {
                            if (staged) historyExports.discard() else file.delete()
                            android.widget.Toast.makeText(this@MainActivity, R.string.history_export_failed, android.widget.Toast.LENGTH_LONG).show()
                        }
                    })
                } else {
                    SecretTextInput { WalletCreationScreen(walletManager = walletManager) }
                }
                if (showRecoveryReset) {
                    AlertDialog(onDismissRequest = { if (!resettingWallet) showRecoveryReset = false },
                        title = { Text(stringResource(R.string.replace_wallet_title)) },
                        text = { Text(stringResource(R.string.replace_wallet_message)) },
                        confirmButton = { Button(enabled = !resettingWallet, onClick = {
                            resettingWallet = true
                            scope.launch {
                                try {
                                    val token = walletManager.sessionAuthority.token()
                                    com.nexatrode.nexawal.logic.RecoveryReset.run(walletManager.sessionAuthority, token,
                                        authenticate = {
                                            if (DeviceAuthGate.isAvailable(this@MainActivity)) {
                                                DeviceAuthGate.authenticate(this@MainActivity,
                                                    getString(R.string.replace_wallet_title), getString(R.string.biometric_unlock_subtitle))
                                            }
                                        }, reset = {
                                            walletManager.resetStoredWalletForRecovery()
                                            showRecoveryReset = false
                                        }, foreground = { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
                                        openSetup = { accessGate.unlock(authenticate = {}, openWallet = {}) })
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    unlockError = error.message ?: getString(R.string.authentication_failed)
                                } finally { resettingWallet = false }
                            }
                        }) { Text(stringResource(R.string.action_replace)) } },
                        dismissButton = { Button(enabled = !resettingWallet,
                            onClick = { showRecoveryReset = false }) { Text(stringResource(R.string.action_cancel)) } },
                    )
                }
            }
        }
    }

    private companion object {
        const val PROTECTED_IDLE_TIMEOUT_MS = 5L * 60L * 1_000L
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun SyncLifecycleEffects(
    walletManager: WalletManager,
    refreshInProgress: Boolean,
    lockProtectedSession: () -> Unit,
    changingConfigurations: () -> Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    val view = LocalView.current

    DisposableEffect(lifecycleOwner, walletManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    walletManager.fiatPrices.onForeground()
                    walletManager.resumeUnprotectedSession()
                    walletManager.startForegroundCatchUp()
                    // If we already look synced (common after interruptions), still refresh
                    // balance/transfers from core — history lives in walletcore/cache, not UI state.
                    if (!walletManager.state.value.refreshInProgress &&
                        walletManager.state.value.walletId != null
                    ) {
                        walletManager.refreshWalletDataSnapshots()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (changingConfigurations()) return@LifecycleEventObserver
                    walletManager.stopForegroundCatchUp()
                    walletManager.fiatPrices.onBackground()
                    walletManager.snapshotState()
                    lockProtectedSession()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshInProgress, notificationPermission.status.isGranted) {
        if (refreshInProgress && !notificationPermission.status.isGranted) {
            notificationPermission.launchPermissionRequest()
        }
    }

    DisposableEffect(refreshInProgress) {
        val previous = view.keepScreenOn
        view.keepScreenOn = refreshInProgress
        onDispose { view.keepScreenOn = previous }
    }
}
