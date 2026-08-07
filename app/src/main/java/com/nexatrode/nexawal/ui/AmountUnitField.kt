package com.nexatrode.nexawal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexatrode.nexawal.R
import com.nexatrode.nexawal.logic.FiatEstimate
import com.nexatrode.nexawal.logic.FiatRate
import com.nexatrode.nexawal.logic.XmrAmount

enum class AmountInputMode {
    XMR,
    FIAT,
}

internal object AmountUnitParsing {
    fun piconero(text: String, mode: AmountInputMode, rate: FiatRate?): Long? {
        return when (mode) {
            AmountInputMode.XMR -> XmrAmount.parsePiconero(text)
            AmountInputMode.FIAT -> {
                val live = rate ?: return null
                FiatEstimate.piconeroFromFiat(text, live)
            }
        }
    }

    fun secondaryLine(piconero: Long?, mode: AmountInputMode, rate: FiatRate?): String? {
        val pico = piconero ?: return null
        return when (mode) {
            AmountInputMode.XMR -> FiatEstimate.liveApproxText(pico, rate, System.currentTimeMillis())
            AmountInputMode.FIAT -> FiatEstimate.formatXmrApprox(pico)
        }
    }

    fun xmrAmountForUri(text: String, mode: AmountInputMode, rate: FiatRate?): String? {
        val pico = piconero(text, mode, rate) ?: return null
        if (pico <= 0L) return null
        return FiatEstimate.formatXmrForInput(pico)
    }

    fun setXmrPiconero(
        piconero: Long,
        onTextChange: (String) -> Unit,
        onModeChange: (AmountInputMode) -> Unit,
    ) {
        onModeChange(AmountInputMode.XMR)
        onTextChange(FiatEstimate.formatXmrForInput(piconero))
    }
}

@Composable
internal fun AmountUnitField(
    text: String,
    onTextChange: (String) -> Unit,
    mode: AmountInputMode,
    onModeChange: (AmountInputMode) -> Unit,
    rate: FiatRate?,
    palette: NexaPalette,
    label: String,
    modifier: Modifier = Modifier,
) {
    val swapAvailable = rate != null
    val unitLabel = when (mode) {
        AmountInputMode.XMR -> "XMR"
        AmountInputMode.FIAT -> rate?.currency ?: "USD"
    }

    LaunchedEffect(rate, mode) {
        if (rate == null && mode == AmountInputMode.FIAT) {
            onModeChange(AmountInputMode.XMR)
        }
    }

    fun swapUnits() {
        val live = rate ?: return
        val pico = AmountUnitParsing.piconero(text, mode, live)
        when (mode) {
            AmountInputMode.XMR -> {
                if (pico != null) onTextChange(FiatEstimate.formatFiatForInput(pico, live))
                onModeChange(AmountInputMode.FIAT)
            }
            AmountInputMode.FIAT -> {
                if (pico != null) onTextChange(FiatEstimate.formatXmrForInput(pico))
                onModeChange(AmountInputMode.XMR)
            }
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(unitLabel, color = palette.secondaryText)
                    if (swapAvailable) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { swapUnits() },
                            modifier = Modifier.a11yMinTouchTarget(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapVert,
                                contentDescription = stringResource(
                                    R.string.a11y_swap_amount_unit,
                                    rate?.currency ?: "USD",
                                ),
                                tint = palette.accent,
                            )
                        }
                    }
                }
            },
            colors = nexaFieldColors(palette),
        )
        AmountUnitParsing.secondaryLine(
            piconero = AmountUnitParsing.piconero(text, mode, rate),
            mode = mode,
            rate = rate,
        )?.let { secondary ->
            Spacer(Modifier.height(4.dp))
            Text(
                secondary,
                color = palette.secondaryText,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }
    }
}
