@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.usage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.RecordUsage
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.feedback.crossfadeTransitions
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.Wand

internal const val USAGE_CHECKING_LABEL = "Checking…"

internal enum class UsageLineKind(
    val icon: ImageVector,
) {
    TRANSACTIONS(Lucide.Receipt),
    RULES(Lucide.Wand),
    CATEGORIES(Lucide.FolderOpen),
    SCHEDULES(Lucide.CalendarClock),
    BALANCE(Lucide.WalletCards),
}

internal data class UsageLine(
    val kind: UsageLineKind,
    val title: String,
    val value: String,
    val count: Long,
)

/** The counts that matter for [subject], in the order the dialogs and the sheet list them. */
internal fun usageLines(
    usage: RecordUsage,
    subject: UsageSubject,
    formatBalance: (Long) -> String,
): List<UsageLine> =
    buildList {
        if (subject == UsageSubject.RULE) {
            add(countLine(UsageLineKind.SCHEDULES, "Schedules", usage.schedules))
            return@buildList
        }
        if (subject == UsageSubject.ACCOUNT) {
            usage.balanceMinor?.let { add(UsageLine(UsageLineKind.BALANCE, "Current balance", formatBalance(it), it)) }
        }
        if (subject == UsageSubject.CATEGORY_GROUP) add(countLine(UsageLineKind.CATEGORIES, "Categories", usage.childCategories))
        add(countLine(UsageLineKind.TRANSACTIONS, "Transactions", usage.transactions))
        add(countLine(UsageLineKind.RULES, "Rules", usage.rules))
    }

private fun countLine(
    kind: UsageLineKind,
    title: String,
    count: Long,
) = UsageLine(kind, title, if (count == 0L) "None" else formatInteger(count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()), count)

/** The counts shown inside a delete or close confirmation, with "Checking…" until they are known. */
@Composable
internal fun UsageSummary(
    state: UsageState,
    subject: UsageSubject,
    formatBalance: (Long) -> String = Long::toString,
) {
    UsageContent(state, subject, formatBalance, linkFor = { null })
}

/** The counts as rows; a row links on when [linkFor] gives it somewhere to go and it has something to show. */
@Composable
internal fun UsageContent(
    state: UsageState,
    subject: UsageSubject,
    formatBalance: (Long) -> String,
    linkFor: (UsageLineKind) -> (() -> Unit)?,
) {
    val reduceMotion = tandemReduceMotion
    AnimatedContent(
        targetState = state,
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(tandemMotionSpec(reduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
                .semantics { liveRegion = LiveRegionMode.Polite },
        transitionSpec = {
            val (enter, exit) = crossfadeTransitions(reduceMotion, TandemMotion.STATUS_CHANGE_MILLIS)
            enter togetherWith exit using null
        },
        contentKey = { it::class },
    ) { shown ->
        when (shown) {
            UsageState.Checking -> {
                UsageChecking()
            }

            UsageState.Unavailable -> {
                InlineMessage("Couldn’t count where this is used on this device.", InlineMessageKind.WARNING)
            }

            is UsageState.Ready -> {
                UsageRows(usageLines(shown.usage, subject, formatBalance), linkFor)
            }
        }
    }
}

@Composable
private fun UsageChecking() {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TandemLayout.compactListRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        TandemLoadingIndicator(Modifier.size(16.dp), tandemPalette.accent, 2.dp)
        Text(USAGE_CHECKING_LABEL, color = tandemPalette.muted, fontSize = TandemTypography.body)
    }
}

@Composable
private fun UsageRows(
    lines: List<UsageLine>,
    linkFor: (UsageLineKind) -> (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth()) {
        lines.forEachIndexed { index, line ->
            if (index > 0) DividerLine()
            val link = linkFor(line.kind)?.takeIf { line.count != 0L && line.kind != UsageLineKind.BALANCE }
            ListRow(
                line.title,
                trailing = line.value,
                leading = RowLeading.Icon(line.kind.icon),
                options =
                    ListRowOptions(
                        dense = true,
                        showChevron = link != null,
                        trailingColor = if (line.count == 0L) tandemPalette.muted else tandemPalette.text,
                    ),
                onClick = link,
            )
        }
    }
}
