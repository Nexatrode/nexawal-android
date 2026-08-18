package com.nexatrode.nexawal.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexatrode.nexawal.BuildConfig
import com.nexatrode.nexawal.DeviceAuthGate
import com.nexatrode.nexawal.MoneroConfig
import com.nexatrode.nexawal.R
import com.nexatrode.nexawal.WalletManager
import com.nexatrode.nexawal.walletcore.WalletCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Must be top-level in Kotlin (local enums are not allowed).
private enum class WalletSetupMode { CREATE, IMPORT }

/** DEBUG-only Import prefill from getenv or local.properties → BuildConfig. */
private fun debugTestMnemonic(): String {
    if (!BuildConfig.DEBUG) return ""
    return System.getenv("NEXAWAL_TEST_MNEMONIC")?.trim()?.takeIf { it.isNotEmpty() }
        ?: BuildConfig.NEXAWAL_TEST_MNEMONIC.trim()
}

private fun debugTestRestoreHeight(): String {
    if (!BuildConfig.DEBUG) return "0"
    val raw = System.getenv("NEXAWAL_TEST_RESTORE_HEIGHT")?.trim()?.takeIf { it.isNotEmpty() }
        ?: BuildConfig.NEXAWAL_TEST_RESTORE_HEIGHT.trim()
    if (raw.isEmpty()) return "0"
    return raw.replace(",", "")
}

/**
 * WalletCreationScreen
 *
 * Android equivalent of iOS `WalletCreationView`:
 * - segmented mode: Create (fast) vs Import
 * - mnemonic text area (paste)
 * - restore height:
 *    - Create: shows suggested fast height (tip - 10) when available, user input hidden
 *    - Import: editable restore height field with tips/warnings
 * - mainnet toggle (kept for parity; you can keep it always true for now)
 * - device-auth opt-in during create/import
 * - single-wallet UX: if a wallet exists on device, confirm before replacing
 *
 * Notes:
 * - Suggested restore height uses a very small HTTP call to the configured node's `/get_info`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletCreationScreen(
    walletManager: WalletManager,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by walletManager.state.collectAsState()
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val technoTheme = remember { MoneroConfig.isTechnoThemeEnabled(context) }
    val palette = rememberNexaPalette(technoTheme)
    val neon = palette.classic

    // UI state
    val modeIndex = remember { mutableIntStateOf(1) } // default to IMPORT like iOS
    val setupMode: WalletSetupMode = if (modeIndex.intValue == 0) WalletSetupMode.CREATE else WalletSetupMode.IMPORT

    val mnemonicInput = remember { mutableStateOf(debugTestMnemonic()) }
    val restoreHeightInput = remember { mutableStateOf(debugTestRestoreHeight()) }
    val isMainnet = remember { mutableStateOf(true) }
    val requireDeviceAuth = remember { mutableStateOf(MoneroConfig.requireDeviceAuth(context)) }

    val isLoading = remember { mutableStateOf(false) }
    val errorText = remember { mutableStateOf<String?>(null) }

    val deviceAuthUnavailableText = stringResource(R.string.device_auth_required_unavailable)
    val activityContextRequiredText = stringResource(R.string.error_activity_context_required)
    val unlockWalletTitle = stringResource(R.string.biometric_unlock_wallet)
    val unlockWalletSubtitle = stringResource(R.string.biometric_unlock_subtitle)
    val noStoredWalletText = stringResource(R.string.no_stored_wallet)
    val suggestedHeightFetchFailedText = stringResource(R.string.suggested_height_fetch_failed)
    val savedNodeText = stringResource(R.string.saved_node)
    val failedSaveFmt = stringResource(R.string.failed_save_fmt)

    // Replace confirm UX (single wallet slot)
    val hasStoredWallet = remember { mutableStateOf<Boolean?>(null) }
    val showReplaceConfirm = remember { mutableStateOf(false) }

    // Suggested restore height (create mode only)
    val suggestedRestoreHeight = remember { mutableStateOf<Long?>(null) }
    val isFetchingSuggestedHeight = remember { mutableStateOf(false) }
    val suggestedHeightError = remember { mutableStateOf<String?>(null) }

    // Pre-wallet node config (same settings.json path as Settings).
    val nodeUrlInput = remember {
        mutableStateOf(walletManager.nodeAddressForDisplay(state.nodeUrl ?: walletManager.defaultNodeUrl()))
    }
    val nodeSaveStatus = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.nodeUrl) {
        nodeUrlInput.value = walletManager.nodeAddressForDisplay(state.nodeUrl ?: walletManager.defaultNodeUrl())
    }

    // CREATE mode: freshly generated mnemonic + "wrote it down" backup gate.
    val generatedMnemonic = remember { mutableStateOf("") }
    val wroteSeedDown = remember { mutableStateOf(false) }
    val challengePositions = remember { mutableStateOf(listOf<Int>()) }
    val challengeAnswers = remember { mutableStateListOf("", "", "") }

    suspend fun regenerateCreateSeed() {
        try {
            val mnemonic = withContext(Dispatchers.Default) { WalletCore.generateMnemonicEnglish() }
            generatedMnemonic.value = mnemonic
            wroteSeedDown.value = false
            val words = mnemonic.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val positions = randomChallengePositions(words.size)
            challengePositions.value = positions
            challengeAnswers.clear()
            repeat(positions.size) { challengeAnswers.add("") }
        } catch (t: Throwable) {
            errorText.value = t.message ?: t.javaClass.simpleName
        }
    }

    LaunchedEffect(Unit) {
        // Authoritative: check persisted wallet presence (metadata exists)
        hasStoredWallet.value = runCatching { walletManager.hasStoredWallet() }.getOrNull()
        // Fetch suggested restore height for create mode (best-effort)
        refreshSuggestedRestoreHeightIfNeeded(
            setupMode = setupMode,
            isMainnet = isMainnet.value,
            nodeUrl = walletManager.currentNodeUrl(),
            isFetching = isFetchingSuggestedHeight,
            suggestedHeight = suggestedRestoreHeight,
            suggestedError = suggestedHeightError,
            fetchFailedMessage = suggestedHeightFetchFailedText,
        )
    }

    LaunchedEffect(setupMode, isMainnet.value) {
        refreshSuggestedRestoreHeightIfNeeded(
            setupMode = setupMode,
            isMainnet = isMainnet.value,
            nodeUrl = walletManager.currentNodeUrl(),
            isFetching = isFetchingSuggestedHeight,
            suggestedHeight = suggestedRestoreHeight,
            suggestedError = suggestedHeightError,
            fetchFailedMessage = suggestedHeightFetchFailedText,
        )
    }

    // Generate a fresh seed (and its backup-confirmation challenges) whenever CREATE mode is entered.
    LaunchedEffect(setupMode) {
        if (setupMode == WalletSetupMode.CREATE) {
            regenerateCreateSeed()
        }
    }

    fun effectiveRestoreHeight(): Long {
        val raw = restoreHeightInput.value.trim().toLongOrNull() ?: 0L
        return if (setupMode == WalletSetupMode.CREATE && raw == 0L) {
            // Feather-style optimization in create mode: if user leaves 0, use tip-10 suggestion if available
            suggestedRestoreHeight.value ?: 0L
        } else {
            raw
        }
    }

    fun challengesAllCorrect(): Boolean {
        val positions = challengePositions.value
        if (positions.isEmpty() || positions.size < 3 || challengeAnswers.size < 3) return false
        val words = generatedMnemonic.value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return positions.indices.all { i ->
            val expected = words.getOrNull(positions[i] - 1) ?: return@all false
            val answer = challengeAnswers.getOrElse(i) { "" }
            expected.trim().equals(answer.trim(), ignoreCase = true)
        }
    }

    fun effectiveMnemonic(): String =
        if (setupMode == WalletSetupMode.CREATE) generatedMnemonic.value else mnemonicInput.value

    fun canSubmit(): Boolean = when (setupMode) {
        WalletSetupMode.IMPORT -> mnemonicInput.value.trim().isNotEmpty() && !isLoading.value
        WalletSetupMode.CREATE ->
            generatedMnemonic.value.trim().isNotEmpty() &&
                wroteSeedDown.value &&
                challengesAllCorrect() &&
                !isLoading.value
    }

    fun submit(replaceExisting: Boolean) {
        errorText.value = null
        isLoading.value = true

        scope.launch {
            try {
                // Persist whatever is in the node field so create/import works even if Save wasn't tapped.
                val trimmedNode = nodeUrlInput.value.trim()
                if (trimmedNode.isNotEmpty()) {
                    walletManager.setNodeUrl(trimmedNode)
                }

                MoneroConfig.setRequireDeviceAuth(
                    context,
                    requireDeviceAuth.value && DeviceAuthGate.isAvailable(context)
                )

                val walletId = walletManager.defaultWalletId()
                val nodeUrl = walletManager.currentNodeUrl()

                walletManager.openWalletFromMnemonic(
                    walletId = walletId,
                    mnemonic = effectiveMnemonic(),
                    restoreHeight = effectiveRestoreHeight(),
                    nodeUrl = nodeUrl,
                    mainnet = isMainnet.value,
                    persist = true,
                    replaceExisting = replaceExisting,
                )

                // Refresh is started inside openWalletFromMnemonic (manager scope) before walletId
                // is published, so it survives disposal of this setup screen.

                // After importing/replacing, refresh persisted-wallet flag
                hasStoredWallet.value = runCatching { walletManager.hasStoredWallet() }.getOrNull()
            } catch (t: Throwable) {
                errorText.value = t.message ?: t.javaClass.simpleName
            } finally {
                isLoading.value = false
            }
        }
    }

    fun unlockStoredWallet() {
        errorText.value = null
        isLoading.value = true

        scope.launch {
            try {
                if (MoneroConfig.requireDeviceAuth(context)) {
                    if (!DeviceAuthGate.isAvailable(context)) {
                        throw IllegalStateException(deviceAuthUnavailableText)
                    }
                    val activity = context as? ComponentActivity
                        ?: throw IllegalStateException(activityContextRequiredText)
                    DeviceAuthGate.authenticate(
                        activity = activity,
                        title = unlockWalletTitle,
                        subtitle = unlockWalletSubtitle
                    )
                }

                val loaded = walletManager.loadStoredWalletOnLaunch()
                if (!loaded) {
                    errorText.value = noStoredWalletText
                } else {
                    walletManager.refreshWalletInBackground()
                }
            } catch (t: Throwable) {
                errorText.value = t.message ?: t.javaClass.simpleName
            } finally {
                isLoading.value = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (neon) "nexawal" else stringResource(R.string.create_wallet),
                        color = palette.primaryText,
                        fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = if (neon) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    titleContentColor = palette.primaryText,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scroll)
        ) {
            val walletSetupLabel = stringResource(R.string.section_wallet_setup)
            Text(
                if (neon) walletSetupLabel.uppercase() else walletSetupLabel,
                color = palette.primaryText,
                fontWeight = FontWeight.Bold,
                fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.wallet_setup_description),
                color = palette.secondaryText,
            )

            if (hasStoredWallet.value == true) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.wallet_already_stored), color = palette.secondaryText)
                Spacer(Modifier.height(8.dp))
                PrimaryActionButton(
                    text = if (isLoading.value) stringResource(R.string.unlocking_ellipsis) else stringResource(R.string.unlock_existing_wallet),
                    onClick = { unlockStoredWallet() },
                    palette = palette,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading.value,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Segmented control: Create vs Import
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val segmentColors = SegmentedButtonDefaults.colors(
                    activeContainerColor = palette.cta,
                    activeContentColor = palette.ctaText,
                    activeBorderColor = palette.border,
                    inactiveContainerColor = palette.card,
                    inactiveContentColor = palette.primaryText,
                    inactiveBorderColor = palette.border,
                )
                SegmentedButton(
                    selected = modeIndex.intValue == 0,
                    onClick = { modeIndex.intValue = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = segmentColors,
                    label = {
                        Text(
                            stringResource(R.string.mode_create),
                            fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                        )
                    }
                )
                SegmentedButton(
                    selected = modeIndex.intValue == 1,
                    onClick = { modeIndex.intValue = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = segmentColors,
                    label = {
                        Text(
                            stringResource(R.string.mode_import),
                            fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                        )
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            when (setupMode) {
                WalletSetupMode.CREATE -> {
                    Text(stringResource(R.string.recovery_seed_label), color = palette.primaryText)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.recovery_seed_instructions),
                        color = palette.secondaryText,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = generatedMnemonic.value,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        label = { Text(stringResource(R.string.recovery_seed_label)) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            color = palette.primaryText,
                        ),
                        singleLine = false,
                        colors = nexaFieldColors(palette),
                    )

                    Spacer(Modifier.height(8.dp))

                    SecondaryActionButton(
                        text = stringResource(R.string.generate_new_seed),
                        onClick = { scope.launch { regenerateCreateSeed() } },
                        palette = palette,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading.value,
                    )

                    Spacer(Modifier.height(12.dp))

                    RowSwitch(
                        label = stringResource(R.string.wrote_seed_down),
                        state = wroteSeedDown,
                        enabled = generatedMnemonic.value.isNotEmpty(),
                        palette = palette,
                    )

                    if (wroteSeedDown.value) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.confirm_backup_prompt), color = palette.primaryText)
                        Spacer(Modifier.height(6.dp))

                        val wordNumberFmt = stringResource(R.string.word_number_fmt)
                        challengePositions.value.forEachIndexed { index, position ->
                            OutlinedTextField(
                                value = challengeAnswers.getOrElse(index) { "" },
                                onValueChange = { value ->
                                    if (index < challengeAnswers.size) {
                                        challengeAnswers[index] = value
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(String.format(wordNumberFmt, position), color = palette.secondaryText) },
                                singleLine = true,
                                colors = nexaFieldColors(palette),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                WalletSetupMode.IMPORT -> {
                    Text(stringResource(R.string.mnemonic_paste_label), color = palette.primaryText)
                    OutlinedTextField(
                        value = mnemonicInput.value,
                        onValueChange = { mnemonicInput.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        label = { Text(stringResource(R.string.mnemonic_paste_label)) },
                        placeholder = { Text(stringResource(R.string.mnemonic_paste_placeholder), color = palette.secondaryText) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            color = palette.primaryText,
                        ),
                        singleLine = false,
                        colors = nexaFieldColors(palette),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when (setupMode) {
                WalletSetupMode.CREATE -> {
                    Text(stringResource(R.string.starting_height_fast_label), color = palette.primaryText)
                    Spacer(Modifier.height(6.dp))

                    when {
                        isFetchingSuggestedHeight.value -> {
                            val fetchingFromNodeText = stringResource(R.string.fetching_from_node)
                            Text(fetchingFromNodeText, color = palette.secondaryText)
                            Spacer(Modifier.height(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.semantics {
                                    contentDescription = fetchingFromNodeText
                                }
                            )
                        }
                        suggestedRestoreHeight.value != null -> {
                            Text(stringResource(R.string.starting_height_fast_fmt, suggestedRestoreHeight.value ?: 0L), color = palette.primaryText)
                        }
                        else -> {
                            Text(stringResource(R.string.starting_height_unavailable), color = palette.secondaryText)
                        }
                    }

                    suggestedHeightError.value?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = palette.danger, modifier = Modifier.a11yAssertiveError())
                    }
                }

                WalletSetupMode.IMPORT -> {
                    Text(stringResource(R.string.restore_height_label), color = palette.primaryText)
                    Spacer(Modifier.height(6.dp))

                    OutlinedTextField(
                        value = restoreHeightInput.value,
                        onValueChange = { restoreHeightInput.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.restore_height_label)) },
                        placeholder = { Text("0", color = palette.secondaryText) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = nexaFieldColors(palette),
                    )

                    Spacer(Modifier.height(6.dp))

                    val height = restoreHeightInput.value.trim().toLongOrNull() ?: 0L
                    if (height == 0L) {
                        Text(stringResource(R.string.restore_height_tip), color = palette.secondaryText)
                    } else {
                        Text(stringResource(R.string.restore_height_warning), color = palette.secondaryText)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Mainnet toggle (parity)
            RowSwitch(
                label = stringResource(R.string.toggle_mainnet),
                state = isMainnet,
                palette = palette,
                testTag = A11yTags.CREATE_MAINNET_SWITCH,
            )

            Spacer(Modifier.height(12.dp))

            RowSwitch(
                label = stringResource(R.string.toggle_require_device_auth),
                state = requireDeviceAuth,
                enabled = DeviceAuthGate.isAvailable(context),
                palette = palette,
                testTag = A11yTags.DEVICE_AUTH_SWITCH,
            )

            Spacer(Modifier.height(6.dp))

            if (DeviceAuthGate.isAvailable(context)) {
                Text(
                    stringResource(R.string.device_auth_enabled_note),
                    color = palette.secondaryText,
                )
            } else {
                Text(
                    stringResource(R.string.device_auth_unavailable),
                    color = palette.secondaryText,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Submit button (Import / Create)
            val importWalletLabel = stringResource(R.string.import_wallet)
            val createWalletLabel = stringResource(R.string.create_wallet)
            val importingWalletLabel = stringResource(R.string.importing_wallet)
            val creatingWalletLabel = stringResource(R.string.creating_wallet)
            val primaryLabel = if (setupMode == WalletSetupMode.IMPORT) importWalletLabel else createWalletLabel
            val loadingLabel = if (setupMode == WalletSetupMode.IMPORT) importingWalletLabel else creatingWalletLabel
            PrimaryActionButton(
                text = if (isLoading.value) loadingLabel else primaryLabel,
                enabled = canSubmit(),
                onClick = {
                    val stored = hasStoredWallet.value == true
                    if (stored) {
                        showReplaceConfirm.value = true
                    } else {
                        submit(replaceExisting = false)
                    }
                },
                palette = palette,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Error section
            val mergedError = errorText.value ?: state.lastError
            if (mergedError != null) {
                Text(stringResource(R.string.error_prefix_fmt, mergedError), color = palette.danger, modifier = Modifier.a11yAssertiveError())
                Spacer(Modifier.height(12.dp))
            }

            // Network & Node (editable before create/import; same persistence as Settings)
            val networkNodeLabel = stringResource(R.string.section_network_node)
            Text(if (neon) networkNodeLabel.uppercase() else networkNodeLabel, color = palette.primaryText)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.daemon_url_label), color = palette.primaryText, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = nodeUrlInput.value,
                onValueChange = { nodeUrlInput.value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.daemon_url_label)) },
                placeholder = { Text(walletManager.defaultNodeUrl(), color = palette.secondaryText) },
                colors = nexaFieldColors(palette),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.node_url_help),
                color = palette.secondaryText,
            )
            Spacer(modifier.height(12.dp))
            PrimaryActionButton(
                text = stringResource(R.string.save_node),
                onClick = {
                    nodeSaveStatus.value = null
                    scope.launch {
                        try {
                            walletManager.setNodeUrl(nodeUrlInput.value)
                            nodeSaveStatus.value = savedNodeText
                            refreshSuggestedRestoreHeightIfNeeded(
                                setupMode = setupMode,
                                isMainnet = isMainnet.value,
                                nodeUrl = walletManager.currentNodeUrl(),
                                isFetching = isFetchingSuggestedHeight,
                                suggestedHeight = suggestedRestoreHeight,
                                suggestedError = suggestedHeightError,
                                fetchFailedMessage = suggestedHeightFetchFailedText,
                            )
                        } catch (t: Throwable) {
                            nodeSaveStatus.value = String.format(failedSaveFmt, t.message ?: t.javaClass.simpleName)
                        }
                    }
                },
                palette = palette,
                modifier = Modifier.fillMaxWidth(),
            )
            nodeSaveStatus.value?.let { status ->
                Spacer(modifier.height(6.dp))
                Text(status, color = palette.secondaryText, modifier = Modifier.a11yPoliteStatus())
            }

            Spacer(modifier.height(12.dp))

            // Info section (iOS parity)
            val infoLabel = stringResource(R.string.section_info)
            Text(if (neon) infoLabel.uppercase() else infoLabel, color = palette.primaryText)
            Spacer(modifier.height(6.dp))
            Text(stringResource(R.string.walletcore_version_fmt, state.version ?: "(unknown)"), color = palette.secondaryText)

            Spacer(modifier.height(24.dp))
        }

        if (showReplaceConfirm.value) {
            ReplaceWalletConfirmDialog(
                palette = palette,
                onCancel = { showReplaceConfirm.value = false },
                onReplace = {
                    showReplaceConfirm.value = false
                    submit(replaceExisting = true)
                }
            )
        }
    }
}

@Composable
private fun ReplaceWalletConfirmDialog(
    palette: NexaPalette,
    onCancel: () -> Unit,
    onReplace: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.replace_wallet_title)) },
        text = {
            Text(stringResource(R.string.replace_wallet_message))
        },
        confirmButton = {
            PrimaryActionButton(text = stringResource(R.string.action_replace), onClick = onReplace, palette = palette)
        },
        dismissButton = {
            SecondaryActionButton(text = stringResource(R.string.action_cancel), onClick = onCancel, palette = palette)
        }
    )
}

@Composable
private fun RowSwitch(
    label: String,
    state: MutableState<Boolean>,
    enabled: Boolean = true,
    palette: NexaPalette,
    testTag: String? = null,
) {
    LabeledSwitchRow(
        label = label,
        checked = state.value,
        onCheckedChange = { state.value = it },
        palette = palette,
        enabled = enabled,
        testTag = testTag,
    )
}

/**
 * Picks up to [count] distinct 1-based word positions (in ascending order) out of [wordCount]
 * words, used to challenge the user on words from the seed they just generated.
 */
private fun randomChallengePositions(wordCount: Int, count: Int = 3): List<Int> {
    if (wordCount <= 0) return emptyList()
    return (1..wordCount).shuffled().take(minOf(count, wordCount)).sorted()
}

private suspend fun refreshSuggestedRestoreHeightIfNeeded(
    setupMode: WalletSetupMode,
    isMainnet: Boolean,
    nodeUrl: String,
    isFetching: MutableState<Boolean>,
    suggestedHeight: MutableState<Long?>,
    suggestedError: MutableState<String?>,
    fetchFailedMessage: String,
) {
    // Only for create mode
    if (setupMode != WalletSetupMode.CREATE) {
        suggestedHeight.value = null
        suggestedError.value = null
        isFetching.value = false
        return
    }

    isFetching.value = true
    suggestedError.value = null
    suggestedHeight.value = null

    // Minimal /get_info call (mirrors iOS MoneroDaemonClient.getInfo usage).
    // We only need target_height to suggest restoreHeight = target_height - 10.
    try {
        val tip = fetchMoneroTargetHeight(nodeUrl)
        val suggested = if (tip > 10L) tip - 10L else 0L
        suggestedHeight.value = suggested
    } catch (t: Throwable) {
        // Non-fatal: this is only used to suggest a fast height.
        suggestedHeight.value = null
        suggestedError.value = fetchFailedMessage
    } finally {
        isFetching.value = false
    }
}

/**
 * Fetch `target_height` from a monerod daemon using `/get_info`.
 *
 * This intentionally avoids introducing a full HTTP client dependency (Retrofit/OkHttp) for bring-up.
 */
private suspend fun fetchMoneroTargetHeight(baseUrl: String): Long {
    return withContext(Dispatchers.IO) {
        val url = java.net.URL(baseUrl.trimEnd('/') + "/get_info")
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            requestMethod = "GET"
            doInput = true
        }

        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("get_info failed: HTTP $code")
        }

        val body = conn.inputStream.bufferedReader().use { it.readText() }

        // Minimal parse: find `"target_height": <number>`
        val key = "\"target_height\""
        val idx = body.indexOf(key)
        if (idx < 0) throw IllegalStateException("target_height not found")
        val colon = body.indexOf(':', idx)
        if (colon < 0) throw IllegalStateException("target_height parse error")
        val end = body.indexOfAny(charArrayOf(',', '\n', '\r', '}'), startIndex = colon + 1).let { if (it < 0) body.length else it }
        body.substring(colon + 1, end).trim().toLong()
    }
}
