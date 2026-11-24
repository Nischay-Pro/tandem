@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.banking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.ActualBankSync
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide

/** Actual's presets for Automatic syncing, in minutes; 0 is Never. */
internal val SYNC_INTERVAL_PRESETS = listOf(0 to "Never", 720 to "Every 12 hours", 1_440 to "Every day", 10_080 to "Every week")

/** Units a custom interval can use, largest first, as Actual offers them. */
internal val SYNC_INTERVAL_UNITS = listOf(10_080 to "week", 1_440 to "day", 60 to "hour", 1 to "minute")

/** How Actual names an interval: a preset's name, else "Every 90 minutes" in the largest unit that divides evenly. */
internal fun syncIntervalLabel(minutes: Int): String {
    SYNC_INTERVAL_PRESETS.firstOrNull { it.first == minutes }?.let { return it.second }
    val (size, unit) = SYNC_INTERVAL_UNITS.first { minutes % it.first == 0 }
    val count = minutes / size
    return if (count == 1) "Every $unit" else "Every $count ${unit}s"
}

/** A custom interval in minutes, at least Actual's 15-minute floor; null when [count] isn't a whole number. */
internal fun customSyncInterval(
    count: String,
    unitMinutes: Int,
): Int? =
    count
        .trim()
        .toIntOrNull()
        ?.takeIf { it >= 1 }
        ?.let { maxOf(it * unitMinutes, ActualBankSync.MIN_SYNC_INTERVAL_MINUTES) }

/** Actual's Automatic syncing: how often linked accounts download new transactions while Tandem is open. */
@Composable
internal fun BankSyncScheduleCard(
    minutes: Int?,
    enabled: Boolean,
    onChoose: (Int) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    SectionCard(
        "Automatic syncing",
        "Downloads new transactions while Tandem is open, once any account hasn’t synced in this long, " +
            "including syncs from other devices. Shared with Actual.",
    ) {
        ListRow(
            "Sync accounts",
            null,
            minutes?.let(::syncIntervalLabel) ?: "…",
            leading = RowLeading.Icon(Lucide.Clock),
            onClick = if (enabled && minutes != null) ({ choosing = true }) else null,
        )
    }
    SyncIntervalDialog(choosing, minutes ?: 0, onDismiss = { choosing = false }) {
        choosing = false
        onChoose(it)
    }
}

@Composable
private fun SyncIntervalDialog(
    visible: Boolean,
    current: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    var custom by remember(visible) { mutableStateOf(SYNC_INTERVAL_PRESETS.none { it.first == current }) }
    TandemChoiceDialog(visible = visible, onDismissRequest = onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TandemModalHeader("Sync accounts", onClose = onDismiss, leadingIcon = Lucide.Clock)
                SYNC_INTERVAL_PRESETS.forEach { (minutes, label) ->
                    IntervalRow(label, selected = !custom && minutes == current) { onPick(minutes) }
                }
                DividerLine()
                IntervalRow("Custom interval", selected = custom) { custom = true }
                if (custom) CustomInterval(current, onPick)
            }
        }
    }
}

@Composable
private fun IntervalRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) = ListRow(
    label,
    leading = RowLeading.Icon(Lucide.Check.takeIf { selected } ?: Lucide.Clock),
    options = ListRowOptions(showChevron = false),
    onClick = onClick,
)

@Composable
private fun CustomInterval(
    current: Int,
    onPick: (Int) -> Unit,
) {
    val start = if (current > 0) SYNC_INTERVAL_UNITS.first { current % it.first == 0 } else SYNC_INTERVAL_UNITS.first { it.first == 60 }
    var unit by remember { mutableStateOf(start.first) }
    var count by remember { mutableStateOf(if (current > 0) (current / start.first).toString() else "2") }
    TextFieldCard(
        "Every",
        count,
        { count = it.filter(Char::isDigit).take(4) },
        TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        SYNC_INTERVAL_UNITS.reversed().forEach { (size, name) ->
            TandemActionButton(
                "${name}s",
                if (unit == size) TandemActionButtonTone.PRIMARY else TandemActionButtonTone.NEUTRAL,
                Modifier.weight(1f),
            ) { unit = size }
        }
    }
    val minutes = customSyncInterval(count, unit)
    TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), enabled = minutes != null) {
        minutes?.let(onPick)
    }
}
