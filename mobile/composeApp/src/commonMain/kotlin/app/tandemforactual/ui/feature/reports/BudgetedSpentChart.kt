@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatReportCurrency
import app.tandemforactual.ui.core.state.ReportKind
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide

@Composable
internal fun BudgetedSpentChart(
    data: ReportDataBundle,
    maxRows: Int?,
    onOpenCategoryActivity: (String, String) -> Unit,
) {
    val points = maxRows?.let { data.budgetedSpent.take(it) } ?: data.budgetedSpent
    val maximum = points.maxOfOrNull { maxOf(it.budgetedMinor, it.spentMinor) }?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
        points.forEach { point ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TandemShapes.control))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "View ${point.category} activity",
                        onClick = { onOpenCategoryActivity(point.categoryId, point.category) },
                    ).padding(vertical = TandemSpacing.extraSmall),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    Text(
                        point.category,
                        modifier = Modifier.weight(1f),
                        color = tandemPalette.text,
                        fontSize = TandemTypography.body,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (point.isOverBudget) {
                        Icon(
                            Lucide.CircleAlert,
                            contentDescription = "Over budget",
                            modifier = Modifier.size(20.dp),
                            tint = tandemPalette.warning,
                        )
                    }
                    Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.muted)
                }
                BudgetedSpentValueBar("Budgeted", point.budgetedMinor, maximum, tandemPalette.positive, data.displaySettings)
                BudgetedSpentValueBar("Spent", point.spentMinor, maximum, tandemPalette.negative, data.displaySettings)
            }
        }
    }
}

@Composable
internal fun BudgetedSpentValueBar(
    label: String,
    valueMinor: Long,
    maxValueMinor: Long,
    color: androidx.compose.ui.graphics.Color,
    displaySettings: BudgetDisplaySettings?,
) {
    val trackColor = tandemPalette.raised
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = tandemPalette.muted, fontSize = TandemTypography.caption, modifier = Modifier.width(76.dp))
            Text(
                amountAnnotatedString(formatReportCurrency(valueMinor, displaySettings), displaySettings?.currencyFractionDigits ?: 2),
                color = color,
                fontSize = TandemTypography.caption,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val fraction = (valueMinor.toDouble() / maxValueMinor.coerceAtLeast(1L).toDouble()).toFloat().coerceIn(0f, 1f)
            val cornerRadius = CornerRadius(TandemShapes.small.toPx())
            drawRoundRect(trackColor, cornerRadius = cornerRadius)
            if (fraction > 0f) {
                drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = cornerRadius)
            }
        }
    }
}

@Composable
internal fun ReportLegend(
    kind: ReportKind,
    data: ReportDataBundle,
) {
    val items =
        when (kind) {
            ReportKind.SPENDING_BY_CATEGORY -> emptyList()
            ReportKind.SPENDING_OVER_TIME -> {
                val average = data.monthly.map(MonthlyMoneyPoint::spendingMinor).averageLong()
                listOf(
                    "Spending" to tandemPalette.negative,
                    "Average ${formatReportCurrency(average, data.displaySettings)}" to tandemPalette.syncing,
                )
            }
            ReportKind.INCOME_AND_SPENDING -> listOf("Income" to tandemPalette.positive, "Spending" to tandemPalette.negative)
            ReportKind.BUDGETED_AND_SPENT -> listOf("Budgeted" to tandemPalette.positive, "Spent" to tandemPalette.negative)
            ReportKind.NET_WORTH -> listOf("Net worth" to tandemPalette.accent)
        }
    if (items.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
            items.forEach { (label, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                ) {
                    Box(Modifier.size(10.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                    Text(
                        amountAnnotatedString(label, data.displaySettings?.currencyFractionDigits ?: 2),
                        color = tandemPalette.muted,
                        fontSize = TandemTypography.caption,
                    )
                }
            }
        }
    }
}
