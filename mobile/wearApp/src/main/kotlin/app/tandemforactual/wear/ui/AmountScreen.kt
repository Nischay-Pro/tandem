@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.wear.ui

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import app.tandemforactual.watch.WatchMoneyFormat
import app.tandemforactual.watch.WatchSummary
import app.tandemforactual.watch.format
import app.tandemforactual.wear.R
import app.tandemforactual.wear.data.AmountEntry

private const val SPOKEN_KEY = "amount"
private const val KEY_WIDTH = 0.22f
private const val KEY_HEIGHT = 0.6f
private const val ROTARY_STEP_PX = 48f

/** A till-style keypad for round screens; the crown turns the amount a whole unit at a time and the mic takes it spoken. */
@Composable
fun AmountScreen(
    draft: QuickAddDraft,
    summary: WatchSummary?,
    onNext: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    val moneyFormat = summary?.money ?: WatchMoneyFormat()
    var travelled by remember { mutableFloatStateOf(0f) }
    val dictation =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val spoken =
                result.data
                    ?.takeIf { result.resultCode == Activity.RESULT_OK }
                    ?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence(SPOKEN_KEY)?.toString() }
            spoken?.let { AmountEntry.spoken(it, moneyFormat.fractionDigits) }?.let { draft.amount = draft.amount.copy(minor = it) }
        }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ScreenScaffold {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onRotaryScrollEvent {
                    travelled += it.verticalScrollPixels
                    val units = (travelled / ROTARY_STEP_PX).toInt()
                    if (units != 0) {
                        travelled -= units * ROTARY_STEP_PX
                        draft.amount = draft.amount.turn(units)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    true
                }.focusRequester(focus)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            val key = maxWidth * KEY_WIDTH
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (draft.amount.spending) "−" else "+") + moneyFormat.format(draft.amount.minor),
                        modifier = Modifier.clickable { draft.amount = draft.amount.flipSign() },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (draft.amount.spending) MaterialTheme.colorScheme.onSurface else WearColors.accent,
                        maxLines = 1,
                    )
                    IconButton(onClick = { dictation.launch(dictationIntent()) }, modifier = Modifier.size(key * KEY_HEIGHT)) {
                        Icon(painterResource(R.drawable.ic_mic), contentDescription = "Say the amount")
                    }
                }
                KEYS.forEach { row -> KeyRow(row, key, draft, onNext) }
            }
        }
    }
}

private val KEYS = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓"))

@Composable
private fun KeyRow(
    keys: List<String>,
    width: Dp,
    draft: QuickAddDraft,
    onNext: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { key ->
            val modifier = Modifier.width(width).height(width * KEY_HEIGHT)
            when (key) {
                "✓" -> {
                    Key(modifier, WearColors.accent, enabled = draft.amount.minor > 0, onClick = onNext) {
                        Icon(painterResource(R.drawable.ic_check), contentDescription = "Next", tint = WearColors.onAccent)
                    }
                }

                "⌫" -> {
                    Key(modifier, Color.Transparent, onClick = { draft.amount = draft.amount.backspace() }) {
                        Icon(painterResource(R.drawable.ic_backspace), contentDescription = "Delete a digit")
                    }
                }

                else -> {
                    Key(modifier, WearColors.surfaceHigh, onClick = { draft.amount = draft.amount.digit(key.toInt()) }) {
                        Text(key, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(
    modifier: Modifier,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (enabled) color else WearColors.surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun dictationIntent(): Intent {
    val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
    RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(SPOKEN_KEY).setLabel("Say the amount").build()))
    return intent
}
