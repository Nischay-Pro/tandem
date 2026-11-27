@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.wear.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.tandemforactual.watch.WatchBill
import app.tandemforactual.watch.WatchCategory
import app.tandemforactual.watch.WatchState
import app.tandemforactual.watch.WatchSummary
import app.tandemforactual.watch.money
import app.tandemforactual.wear.R
import app.tandemforactual.wear.data.WatchText

/** The glance, with upcoming bills one swipe to the side. */
@Composable
fun GlancePager(
    summary: WatchSummary?,
    waiting: Int,
    phoneHasTandem: Boolean?,
    onCategory: (String) -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    onInstallOnPhone: () -> Unit,
) {
    val pagerState = rememberPagerState { 2 }
    HorizontalPagerScaffold(pagerState = pagerState) {
        HorizontalPager(state = pagerState) { page ->
            AnimatedPage(page, pagerState) {
                if (page == 0) {
                    GlanceScreen(summary, waiting, phoneHasTandem, onCategory, onAdd, onRefresh, onInstallOnPhone)
                } else {
                    BillsScreen(summary)
                }
            }
        }
    }
}

@Composable
private fun GlanceScreen(
    summary: WatchSummary?,
    waiting: Int,
    phoneHasTandem: Boolean?,
    onCategory: (String) -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    onInstallOnPhone: () -> Unit,
) {
    val missingOnPhone = summary == null && phoneHasTandem == false
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val canAdd = summary?.quickAdd == true
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            if (canAdd) {
                EdgeButton(onClick = onAdd) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "Add a transaction")
                }
            }
        },
    ) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
            item {
                ListHeader(Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(summary?.budgetName ?: "Tandem", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item { LeftToSpend(summary, missingOnPhone, Modifier.transformedHeight(this, spec)) }
            if (missingOnPhone) {
                item {
                    FilledTonalButton(
                        onClick = onInstallOnPhone,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = { Text("Install on phone") },
                    )
                }
            } else if (summary == null || summary.state == WatchState.LOCKED) {
                item {
                    FilledTonalButton(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = { Text("Check again") },
                    )
                }
            }
            val nextBill = summary?.takeIf { it.nextBillOnGlance && it.state != WatchState.LOCKED }?.bills?.firstOrNull()
            if (summary != null && nextBill != null) {
                item(key = "next-bill") { NextBillPanel(summary, nextBill, Modifier.transformedHeight(this, spec)) }
            }
            summary?.categories?.takeIf { summary.categoriesOnGlance }?.forEach { category ->
                item(key = category.id) {
                    CategoryCard(
                        summary,
                        category,
                        Modifier.transformedHeight(this, spec),
                        SurfaceTransformation(spec),
                    ) { onCategory(category.id) }
                }
            }
            if (waiting > 0) {
                item {
                    Text(
                        if (waiting == 1) "1 transaction waiting for your phone" else "$waiting transactions waiting for your phone",
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun LeftToSpend(
    summary: WatchSummary?,
    missingOnPhone: Boolean,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            missingOnPhone -> Hint(WatchText.NO_PHONE_APP)
            summary == null -> Hint(WatchText.NO_BUDGET)
            summary.state == WatchState.LOCKED -> Hint(WatchText.LOCKED)
            else -> {
                val headline = WatchText.headline(summary)
                Text(
                    summary.money(headline.minor),
                    style = MaterialTheme.typography.displayMedium,
                    color = if ((headline.minor ?: 0) < 0) WearColors.over else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(headline.label, style = MaterialTheme.typography.labelMedium, color = WearColors.muted)
                headline.secondary?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = WearColors.muted, textAlign = TextAlign.Center)
                }
                WatchText.synced(summary)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = WearColors.muted)
                }
            }
        }
    }
}

@Composable
private fun NextBillPanel(
    summary: WatchSummary,
    bill: WatchBill,
    modifier: Modifier,
) {
    Panel(modifier) {
        LabelledRow("Next: ${bill.name}", WatchText.billAmount(summary, bill.amountMinor))
        Text(WatchText.billDate(bill.date), style = MaterialTheme.typography.labelSmall, color = WearColors.muted)
    }
}

@Composable
fun Hint(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = WearColors.muted,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CategoryCard(
    summary: WatchSummary,
    category: WatchCategory,
    modifier: Modifier,
    transformation: SurfaceTransformation,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), transformation = transformation) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                category.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                WatchText.categoryStatus(summary, category),
                style = MaterialTheme.typography.labelMedium,
                color = if (category.over && summary.amountsShown) WearColors.over else WearColors.muted,
                maxLines = 1,
            )
        }
        if (summary.amountsShown) {
            Spacer(Modifier.height(6.dp))
            SpendBar(category.progress, category.over)
        }
    }
}

/** How much of a category is spent; it turns to the warning colour once it's over. */
@Composable
fun SpendBar(
    progress: Float,
    over: Boolean,
) {
    val shown by animateFloatAsState(progress, tween(durationMillis = 600), label = "spent")
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(shape)
            .background(WearColors.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(shown)
                .height(4.dp)
                .clip(shape)
                .background(if (over) WearColors.over else WearColors.accent),
        )
    }
}
