@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.BudgetChangeHistory
import app.tandemforactual.data.actual.ChangeSet
import app.tandemforactual.ui.core.format.platformFormatTimeAtEpochMilliseconds
import app.tandemforactual.ui.core.format.platformIsoDateAtEpochMilliseconds
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Undo2
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.Wand
import kotlinx.coroutines.launch

/** Every recent edit to the budget, newest first, from this device and others, with undo. */
@Composable
internal fun ChangeHistoryScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    deviceName: String,
) {
    val scope = rememberCoroutineScope()
    var history by remember(state.selectedBudgetId) { mutableStateOf<BudgetChangeHistory?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(ChangeFilter.ALL) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<Pair<String, InlineMessageKind>?>(null) }
    val password = state.encryptedPassword.takeIf { it.isNotBlank() }
    LaunchedEffect(state.selectedBudgetId, reload) {
        loading = true
        history = mobileSession.insights.loadChangeHistory(state.selectedBudgetId, password)
        loading = false
        history?.deviceNode?.takeIf { it !in mobileSession.planning.deviceNames }?.let {
            mobileSession.planning.nameDevice(
                state.selectedBudgetId,
                it,
                deviceName,
            )
        }
    }
    val names =
        remember(mobileSession.payeeRecords, mobileSession.categoryRecords, mobileSession.accountRecords) {
            (mobileSession.payeeRecords + mobileSession.categoryRecords + mobileSession.accountRecords)
                .mapNotNull { record -> record.recordName()?.let { record.recordId to it } }
                .toMap()
        }
    val describe = ChangeDescriber(names, state)
    val visible =
        history
            ?.changes
            .orEmpty()
            .mapNotNull { set ->
                set.copy(rows = set.rows.filter { it.dataset !in INTERNAL_DATASETS }).takeIf { it.rows.isNotEmpty() }
            }.filter { set ->
                when (filter) {
                    ChangeFilter.ALL -> true
                    ChangeFilter.TRANSACTIONS -> set.rows.any { it.dataset == "transactions" }
                    ChangeFilter.BUDGET -> set.rows.any { it.dataset in BUDGET_DATASETS }
                    ChangeFilter.SETUP -> set.rows.any { it.dataset in SETUP_DATASETS }
                    ChangeFilter.OTHER_DEVICES -> set.node != history?.deviceNode
                }
            }
    PageLazyColumn(
        verticalSpacing = TandemSpacing.small,
        isRefreshing = loading && history != null,
        onRefresh = { reload++ },
    ) {
        item(key = "filters") {
            FilterPills(
                ChangeFilter.entries.map {
                    it to it.label
                },
                filter,
                { filter = it },
                contentPadding = PaddingValues(bottom = TandemSpacing.small),
            )
        }
        notice?.let { (text, kind) -> item(key = "notice") { InlineMessage(text, kind, onDismiss = { notice = null }) } }
        when {
            history == null && loading ->
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(TandemSpacing.extraLarge), contentAlignment = Alignment.Center) {
                        TandemLoadingIndicator(Modifier.size(24.dp), tandemPalette.accent, 2.dp)
                    }
                }
            history == null ->
                item(key = "error") {
                    EmptyState("Couldn’t read the change history", "Open the budget while connected to Actual, then try again.")
                }
            visible.isEmpty() ->
                item(key = "empty") {
                    EmptyState("No changes yet", "Edits made on any device after this one opened the budget appear here.")
                }
        }
        var lastDay: String? = null
        visible.forEach { set ->
            val day = platformIsoDateAtEpochMilliseconds(set.millis)
            if (day != lastDay) {
                lastDay = day
                item(key = "day-$day") { DayHeader(day) }
            }
            item(key = set.id) {
                ChangeRow(
                    set = set,
                    describe = describe,
                    deviceLabel =
                        if (set.node ==
                            history?.deviceNode
                        ) {
                            "This device"
                        } else {
                            mobileSession.planning.deviceNames[set.node] ?: "Another device"
                        },
                    expanded = expanded == set.id,
                    undoing = mobileSession.isPerformingBudgetAction,
                    onToggle = { expanded = if (expanded == set.id) null else set.id },
                    onUndo = {
                        scope.launch {
                            val result = mobileSession.insights.undoChange(state.selectedBudgetId, set.id, password)
                            notice =
                                when {
                                    result == null ->
                                        (mobileSession.budgetActionMessage ?: "Couldn’t undo this change.") to
                                            InlineMessageKind.ERROR
                                    result.skipped > 0 && result.reverted == 0 ->
                                        "Nothing to undo. These fields were changed again later." to InlineMessageKind.WARNING
                                    result.skipped > 0 ->
                                        "Undone, except fields that were changed again later." to
                                            InlineMessageKind.SUCCESS
                                    else -> "Change undone." to InlineMessageKind.SUCCESS
                                }
                            expanded = null
                            reload++
                        }
                    },
                )
            }
        }
    }
}

private enum class ChangeFilter(
    val label: String,
) {
    ALL("All"),
    TRANSACTIONS("Transactions"),
    BUDGET("Budget"),
    SETUP("Payees & rules"),
    OTHER_DEVICES("Other devices"),
}

private val INTERNAL_DATASETS =
    setOf(
        "payee_mapping",
        "category_mapping",
        "preferences",
        "messages_clock",
        "schedules_next_date",
        "schedules_json_paths",
        "zero_budget_months",
    )

private val BUDGET_DATASETS = setOf("zero_budgets", "reflect_budgets", "zero_budget_months", "categories", "category_groups")

private val SETUP_DATASETS = setOf("payees", "rules", "schedules", "payee_mapping", "category_mapping")

@Composable
private fun DayHeader(isoDay: String) {
    val today = platformTodayIsoDate()
    Text(
        when (isoDay) {
            today -> "Today"
            else -> formatLiveDate(isoDay)
        },
        Modifier.padding(top = TandemSpacing.medium, bottom = TandemSpacing.xxSmall),
        color = tandemPalette.muted,
        fontSize = TandemTypography.label,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ChangeRow(
    set: ChangeSet,
    describe: ChangeDescriber,
    deviceLabel: String,
    expanded: Boolean,
    undoing: Boolean,
    onToggle: () -> Unit,
    onUndo: () -> Unit,
) {
    val palette = tandemPalette
    val primary = set.rows.first()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.large))
            .background(if (expanded) palette.surface else palette.background)
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide details" else "Show details", onClick = onToggle)
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(palette.surface), contentAlignment = Alignment.Center) {
                Icon(datasetIcon(primary.dataset), null, Modifier.size(20.dp), tint = palette.text)
            }
            Column(Modifier.weight(1f).padding(start = TandemSpacing.medium)) {
                Text(
                    describe.title(set),
                    color = palette.text,
                    fontSize = TandemTypography.action,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${platformFormatTimeAtEpochMilliseconds(set.millis)} · $deviceLabel",
                    color = palette.muted,
                    fontSize = TandemTypography.label,
                )
            }
        }
        AnimatedReveal(visible = expanded) {
            Column(Modifier.padding(top = TandemSpacing.medium), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                set.rows.take(MAX_DETAIL_ROWS).forEach { row ->
                    describe.details(row).forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(0.4f), color = palette.muted, fontSize = TandemTypography.compactBody)
                            Text(
                                value,
                                Modifier.weight(0.6f),
                                color = palette.text,
                                fontSize = TandemTypography.compactBody,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (set.rows.size > MAX_DETAIL_ROWS) {
                    Text("and ${set.rows.size - MAX_DETAIL_ROWS} more", color = palette.muted, fontSize = TandemTypography.compactBody)
                }
                TandemActionButton(
                    "Undo this change",
                    TandemActionButtonTone.NEUTRAL,
                    Modifier.fillMaxWidth().padding(top = TandemSpacing.small),
                    icon = Lucide.Undo2,
                    loading = undoing,
                    onClick = onUndo,
                )
            }
        }
    }
}

private const val MAX_DETAIL_ROWS = 6

private fun datasetIcon(dataset: String): ImageVector =
    when (dataset) {
        "transactions" -> Lucide.Receipt
        "payees", "payee_mapping" -> Lucide.Store
        "categories", "category_groups", "category_mapping" -> Lucide.Tags
        "accounts" -> Lucide.WalletCards
        "rules" -> Lucide.Wand
        "schedules" -> Lucide.CalendarClock
        "zero_budgets", "reflect_budgets", "zero_budget_months" -> Lucide.PiggyBank
        else -> Lucide.FileText
    }
