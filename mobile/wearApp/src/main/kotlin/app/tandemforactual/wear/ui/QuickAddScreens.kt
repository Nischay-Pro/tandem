@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.tandemforactual.watch.WatchChoice
import app.tandemforactual.watch.WatchMoneyFormat
import app.tandemforactual.watch.WatchSummary
import app.tandemforactual.watch.format
import app.tandemforactual.wear.R
import kotlinx.coroutines.delay

@Composable
fun CategoryChoiceScreen(
    choices: List<WatchChoice>,
    skipLabel: String = "No category",
    onChoose: (WatchChoice?) -> Unit,
) {
    ChoiceList("Category", choices.map { it.name }, skipLabel) { index ->
        onChoose(index?.let(choices::get))
    }
}

@Composable
fun PayeeChoiceScreen(
    payees: List<String>,
    onChoose: (String?) -> Unit,
) {
    ChoiceList("Payee", payees, skipLabel = "Skip") { index -> onChoose(index?.let(payees::get)) }
}

/** A short list to pick one from, with a way past it; null means the person skipped. */
@Composable
private fun ChoiceList(
    title: String,
    labels: List<String>,
    skipLabel: String,
    onChoose: (Int?) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
            header(title, spec)
            labels.forEachIndexed { index, label ->
                item {
                    FilledTonalButton(
                        onClick = { onChoose(index) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = { onChoose(null) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(skipLabel) },
                )
            }
        }
    }
}

private fun TransformingLazyColumnScope.header(
    title: String,
    spec: TransformationSpec,
) {
    item {
        ListHeader(Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) { Text(title) }
    }
}

@Composable
fun ConfirmScreen(
    draft: QuickAddDraft,
    summary: WatchSummary?,
    onConfirm: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    var sending by remember { mutableStateOf(false) }
    val money = summary?.money ?: WatchMoneyFormat()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = {
                    if (!sending) {
                        sending = true
                        onConfirm()
                    }
                },
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = "Add")
            }
        },
    ) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
            header("Add", spec)
            item {
                Text(
                    (if (draft.amount.spending) "−" else "+") + money.format(draft.amount.minor),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
            item {
                Panel(Modifier.transformedHeight(this, spec)) {
                    LabelledRow("Category", draft.categoryName ?: "None")
                    draft.payee?.let { LabelledRow("Payee", it) }
                    summary?.accountName?.let { LabelledRow("Account", it) }
                }
            }
        }
    }
}

/** A brief word that the entry is on its way, then back to the glance. */
@Composable
fun SentScreen(
    delivered: Boolean,
    onDone: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(SENT_SHOWN_MS)
        onDone()
    }
    ScreenScaffold {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = WearColors.accent,
            )
            Text(
                if (delivered) "Sent to your phone" else "Saved on the watch",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (!delivered) {
                Text(
                    "It goes to Tandem when your phone is near.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WearColors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private const val SENT_SHOWN_MS = 1_600L
