package com.nexatrode.nexawal

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.nexatrode.nexawal.ui.theme.NexawalTheme

class MainActivity : ComponentActivity() {
    private lateinit var walletManager: WalletManager
    private val accessGate = WalletAccessGate()
    private val sessionHandler = Handler(Looper.getMainLooper())
    private val idleLockAction = Runnable { lockProtectedSession(closeCore = true) }

    private fun lockProtectedSession(closeCore: Boolean = false) {
        sessionHandler.removeCallbacks(idleLockAction)
        if (!::walletManager.isInitialized || walletManager.state.value.walletId == null) return
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        walletManager = (application as NexaWalApp).walletManager

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
                val scope = rememberCoroutineScope()
                var unlockError by remember { mutableStateOf<String?>(null) }

                SyncLifecycleEffects(
                    walletManager = walletManager,
                    refreshInProgress = state.refreshInProgress,
                    lockProtectedSession = { lockProtectedSession(closeCore = false) },
                )

                suspend fun unlockWallet() {
                    unlockError = null
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
                            // A retained manager may be syncing. Do not reopen/reset it on rotation.
                            if (walletManager.state.value.walletId == null && walletManager.hasStoredWallet()) {
                                val loaded = walletManager.loadStoredWalletOnLaunch()
                                check(loaded) { getString(R.string.authentication_failed) }
                                walletManager.refreshWalletInBackground()
                            } else if (walletManager.state.value.walletId != null && walletManager.hasStoredWallet()) {
                                check(walletManager.unlockStoredMetadataSession()) {
                                    getString(R.string.authentication_failed)
                                }
                            }
                        })
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
                    }
                } else if (state.walletId != null) {
                    AppScaffold(walletManager = walletManager)
                } else {
                    WalletCreationScreen(walletManager = walletManager)
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
                    walletManager.stopForegroundCatchUp()
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
