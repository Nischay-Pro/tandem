@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.insights
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.SearchableBudgetTransaction
import app.tandemforactual.data.planning.TrendDirection
import app.tandemforactual.ui.core.format.platformFormatMonth
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.observeActivitySearchRecords
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.ArrowDownRight
import com.composables.icons.lucide.ArrowUpRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus

/** The open budget's transactions as stored on the device, or null until they've loaded. */
@Composable
internal fun rememberBudgetTransactions(
    state: AppState,
    mobileSession: TandemMobileSession,
): List<SearchableBudgetTransaction>? {
    val budgetId = state.selectedBudgetId
    LaunchedEffect(budgetId, mobileSession.databaseGeneration) {
        if (budgetId.isNotBlank()) mobileSession.observeActivitySearchRecords(budgetId)
    }
    return mobileSession.activitySearchRecords.takeIf { mobileSession.activitySearchRecordsBudgetId == budgetId && budgetId.isNotBlank() }
}

/** Ids of categories that hold income, either flagged themselves or sitting in an income group. */
internal fun TandemMobileSession.incomeCategoryIds(): Set<String> {
    val incomeGroups =
        categoryGroupRecords
            .filter { it.recordBody()?.booleanValue("is_income") == true }
            .mapTo(mutableSetOf()) { it.recordId }
    return categoryRecords
        .filter { record ->
            val body = record.recordBody()
            body?.booleanValue("is_income") == true ||
                (body?.stringValue("group") ?: body?.stringValue("cat_group") ?: body?.stringValue("group_id")) in incomeGroups
        }.mapTo(mutableSetOf()) { it.recordId }
}

@Composable
internal fun TandemMobileSession.rememberRecordNames(): RecordNames =
    remember(categoryRecords, payeeRecords, accountRecords) {
        RecordNames(
            categoryRecords.mapNotNull { r -> r.recordName()?.let { r.recordId to it } }.toMap(),
            payeeRecords.mapNotNull { r -> r.recordName()?.let { r.recordId to it } }.toMap(),
            accountRecords.mapNotNull { r -> r.recordName()?.let { r.recordId to it } }.toMap(),
        )
    }

internal data class RecordNames(
    val categories: Map<String, String>,
    val payees: Map<String, String>,
    val accounts: Map<String, String>,
) {
    fun category(id: String?): String = id?.takeIf { it.isNotEmpty() }?.let { categories[it] ?: "Deleted category" } ?: "Uncategorized"
}

/** A month a number of months after the current one, written out, such as “March 2027”. */
internal fun monthFromNow(
    today: String,
    offset: Int,
): String {
    val index = today.take(4).toInt() * 12 + today.substring(5, 7).toInt() - 1 + offset
    return platformFormatMonth(index / 12, index % 12 + 1, abbreviated = false)
}

/** A whole percentage from basis points, such as 1,250 as “13%”. */
internal fun wholePercent(basisPoints: Int): String = "${(kotlin.math.abs(basisPoints) + 50) / 100}%"

/** Spending going up reads as a warning and going down as good news. */
@Composable
internal fun TrendDirection.color(): Color =
    when (this) {
        TrendDirection.UP -> tandemPalette.negative
        TrendDirection.DOWN -> tandemPalette.positive
        TrendDirection.FLAT -> tandemPalette.muted
    }

/** A small arrow and percentage showing which way spending moved. */
@Composable
internal fun TrendBadge(
    direction: TrendDirection,
    changeBasisPoints: Int?,
    modifier: Modifier = Modifier,
) {
    val tint = direction.color()
    val label =
        when {
            direction == TrendDirection.FLAT -> "About the same"
            changeBasisPoints == null -> "New"
            else -> wholePercent(changeBasisPoints)
        }
    Row(
        modifier
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.xxSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (direction) {
                TrendDirection.UP -> Lucide.ArrowUpRight
                TrendDirection.DOWN -> Lucide.ArrowDownRight
                TrendDirection.FLAT -> Lucide.Minus
            },
            contentDescription =
                when (direction) {
                    TrendDirection.UP -> "Up"
                    TrendDirection.DOWN -> "Down"
                    TrendDirection.FLAT -> null
                },
            modifier = Modifier.size(14.dp),
            tint = tint,
        )
        Text(
            label,
            Modifier.padding(start = TandemSpacing.xxSmall),
            color = tint,
            fontSize = TandemTypography.caption,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** A thin rounded bar filled to [fraction], animating when the value changes. */
@Composable
internal fun ProgressBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    marker: Float? = null,
) {
    val palette = tandemPalette
    val shown by animateFloatAsState(
        fraction.coerceIn(0f, 1f),
        tandemMotionSpec(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
        label = "insight-progress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(palette.raised),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown)
                .clip(CircleShape)
                .background(color),
        )
        marker?.let { point ->
            Box(
                Modifier
                    .fillMaxWidth(point.coerceIn(0.01f, 1f))
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.size(width = 2.dp, height = 6.dp).background(palette.text))
            }
        }
    }
}
