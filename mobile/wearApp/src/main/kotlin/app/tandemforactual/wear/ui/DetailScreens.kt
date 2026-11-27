@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.tandemforactual.watch.WatchCategory
import app.tandemforactual.watch.WatchState
import app.tandemforactual.watch.WatchSummary
import app.tandemforactual.watch.money
import app.tandemforactual.wear.data.WatchText

@Composable
fun BillsScreen(summary: WatchSummary?) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
            item {
                ListHeader(Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text("Upcoming bills")
                }
            }
            val bills = summary?.bills.orEmpty()
            when {
                summary == null -> item { Hint(WatchText.NO_BUDGET) }
                summary.state == WatchState.LOCKED -> item { Hint(WatchText.LOCKED) }
                bills.isEmpty() -> item { Hint("Nothing due in the next month") }
            }
            bills.forEach { bill ->
                item {
                    Panel(Modifier.transformedHeight(this, spec)) {
                        LabelledRow(bill.name, WatchText.billAmount(checkNotNull(summary), bill.amountMinor))
                        Text(WatchText.billDate(bill.date), style = MaterialTheme.typography.labelSmall, color = WearColors.muted)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryScreen(
    summary: WatchSummary?,
    category: WatchCategory?,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
            item {
                ListHeader(Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(category?.name ?: "Category", maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (summary == null || category == null) {
                item { Hint("This category is no longer on the watch") }
                return@TransformingLazyColumn
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).transformedHeight(this, spec)) {
                    LabelledRow("Budgeted", summary.money(category.budgetedMinor))
                    LabelledRow("Spent", summary.money(category.spentMinor))
                    LabelledRow(
                        if (category.over) "Over" else "Left",
                        summary.money(category.leftMinor?.let { kotlin.math.abs(it) }),
                        category.over,
                    )
                    if (summary.amountsShown) SpendBar(category.progress, category.over)
                }
            }
            if (category.recent.isNotEmpty()) {
                item {
                    ListHeader(Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                        Text("Recent", color = WearColors.muted)
                    }
                }
            }
            category.recent.forEach { transaction ->
                item {
                    Panel(Modifier.transformedHeight(this, spec)) {
                        LabelledRow(transaction.payee, summary.money(transaction.amountMinor))
                        Text(WatchText.billDate(transaction.date), style = MaterialTheme.typography.labelSmall, color = WearColors.muted)
                    }
                }
            }
        }
    }
}

/** A quiet, untappable surface for figures that don't lead anywhere. */
@Composable
fun Panel(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(WearColors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
fun LabelledRow(
    label: String,
    value: String,
    warn: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = if (warn) WearColors.over else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
