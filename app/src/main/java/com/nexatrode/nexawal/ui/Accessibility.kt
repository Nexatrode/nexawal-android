package com.nexatrode.nexawal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp

/** Stable test tags for Compose UI accessibility tests. */
object A11yTags {
    const val BOTTOM_NAV = "a11y_bottom_nav"
    const val TRANSFER_ROW = "a11y_transfer_row"
    const val SCAN_QR = "a11y_scan_qr"
    const val RECEIVE_QR = "a11y_receive_qr"
    const val CLASSIC_UI_SWITCH = "a11y_classic_ui_switch"
    const val CREATE_MAINNET_SWITCH = "a11y_create_mainnet_switch"
    const val SYNC_PROGRESS = "a11y_sync_progress"
    const val STATUS_LIVE = "a11y_status_live"
    const val ERROR_LIVE = "a11y_error_live"
    const val NETWORK_POLICY = "a11y_network_policy"
    const val DEVICE_AUTH_SWITCH = "a11y_device_auth_switch"
}

fun Modifier.a11yPoliteStatus(): Modifier =
    semantics {
        liveRegion = LiveRegionMode.Polite
        testTag = A11yTags.STATUS_LIVE
    }

fun Modifier.a11yAssertiveError(): Modifier =
    semantics {
        liveRegion = LiveRegionMode.Assertive
        testTag = A11yTags.ERROR_LIVE
    }

fun Modifier.a11ySyncProgress(progress: Float, stateDescription: String): Modifier =
    semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(current = progress.coerceIn(0f, 1f), range = 0f..1f)
        contentDescription = stateDescription
        testTag = A11yTags.SYNC_PROGRESS
    }

fun Modifier.a11yHeading(): Modifier =
    semantics { heading() }

/**
 * Label + switch as one TalkBack node with Role.Switch.
 */
@Composable
internal fun LabeledSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    palette: NexaPalette,
    enabled: Boolean = true,
    description: String? = null,
    testTag: String? = null,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
        .then(
            if (testTag != null) {
                Modifier.semantics { this.testTag = testTag }
            } else {
                Modifier
            }
        )

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = palette.primaryText)
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, color = palette.secondaryText)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = nexaSwitchColors(palette),
        )
    }
}

/**
 * Selectable option with radio semantics (e.g. network policy).
 */
fun Modifier.a11yRadioOption(selected: Boolean, label: String): Modifier =
    semantics {
        role = Role.RadioButton
        this.selected = selected
        contentDescription = label
    }

/**
 * Clickable list option that can expose selected state (e.g. subaddress picker).
 */
fun Modifier.a11ySelectableOption(selected: Boolean): Modifier =
    semantics {
        this.selected = selected
        role = Role.RadioButton
    }

/** Prefer Material minimum touch target for icon-only controls. */
fun Modifier.a11yMinTouchTarget(): Modifier =
    this.then(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))

/**
 * Merge a visible label + value into one spoken node for TalkBack.
 */
fun Modifier.a11yKeyValue(label: String, value: String): Modifier =
    semantics(mergeDescendants = true) {
        contentDescription = "$label: $value"
    }
