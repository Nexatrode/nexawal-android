package com.nexatrode.nexawal.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexatrode.nexawal.MoneroConfig
import com.nexatrode.nexawal.R
import kotlin.system.exitProcess

/**
 * Blocking first-run / re-accept gate for NexaWal terms.
 * Shown when accepted terms version is behind [MoneroConfig.CURRENT_TERMS_VERSION].
 */
@Composable
fun TermsAcceptanceScreen(
    onAccepted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val technoTheme = remember { MoneroConfig.isTechnoThemeEnabled(context) }
    val palette = rememberNexaPalette(technoTheme)
    val neon = palette.classic
    val hasCheckedAgree = remember { mutableStateOf(false) }
    val showFullTerms = remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    if (showFullTerms.value) {
        Dialog(
            onDismissRequest = { showFullTerms.value = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            LegalDocumentScreen(
                document = LegalDocument.Terms,
                onClose = { showFullTerms.value = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scroll),
            ) {
                Text(
                    text = stringResource(R.string.terms_title),
                    color = palette.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.terms_body_custody),
                    color = palette.secondaryText,
                    fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.terms_body_as_is),
                    color = palette.secondaryText,
                    fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.terms_body_node),
                    color = palette.secondaryText,
                    fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                )
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { showFullTerms.value = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.terms_review_full),
                    color = palette.accent,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Checkbox,
                        onClick = { hasCheckedAgree.value = !hasCheckedAgree.value },
                    )
                    .padding(vertical = 4.dp),
            ) {
                Checkbox(
                    checked = hasCheckedAgree.value,
                    onCheckedChange = { hasCheckedAgree.value = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = palette.accent,
                        uncheckedColor = palette.separator,
                        checkmarkColor = palette.ctaText,
                    ),
                )
                Text(
                    text = stringResource(R.string.terms_checkbox),
                    color = palette.primaryText,
                    fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            PrimaryActionButton(
                text = stringResource(R.string.terms_agree),
                onClick = {
                    MoneroConfig.acceptCurrentTerms(context)
                    onAccepted()
                },
                palette = palette,
                enabled = hasCheckedAgree.value,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SecondaryActionButton(
                text = stringResource(R.string.terms_quit),
                onClick = {
                    (context as? Activity)?.finishAffinity()
                    exitProcess(0)
                },
                palette = palette,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
