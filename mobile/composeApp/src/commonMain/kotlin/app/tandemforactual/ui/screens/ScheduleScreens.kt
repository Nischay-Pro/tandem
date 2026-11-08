@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.components.CollapsibleSectionCard
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SelectableTile
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.LiveScheduleSummary
import app.tandemforactual.ui.model.recordName
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Composable
internal fun UpcomingBillsCard(
    state: AppState,
    navigation: AppNavigation,
) {
    val palette = tandemPalette
    CollapsibleSectionCard(
        "Upcoming",
        "Two bills before the end of the month",
        "View all",
        onClick = { openPreviewCategoryGroup(state, navigation, "sample-essentials") },
        expanded = !state.isBudgetSectionCollapsed("sample:upcoming"),
        onToggleExpanded = { state.toggleBudgetSection("sample:upcoming") },
    ) {
        ListRow(
            "Electric Company",
            "Utilities · due ${formatShortDate(SampleDate(2026, 5, 18))}",
            state.formatCurrency(12_000),
            leading = RowLeading.Icon(financeIcon("Utilities")),
            options = ListRowOptions(trailingColor = palette.text),
        )
        DividerLine()
        ListRow(
            "Rent",
            "Essentials · due ${formatShortDate(SampleDate(2026, 6, 1))}",
            state.formatCurrency(150_000),
            leading = RowLeading.Icon(financeIcon("Rent")),
            options = ListRowOptions(trailingColor = palette.text),
        )
    }
}

@Composable
internal fun LiveUpcomingBillsCard(
    state: AppState,
    navigation: AppNavigation,
    schedules: List<LiveScheduleSummary>,
) {
    val sectionKey = "live:${state.selectedBudgetId}:upcoming"
    val expanded = !state.isBudgetSectionCollapsed(sectionKey)
    CollapsibleSectionCard(
        "Upcoming bills",
        "${schedules.size} scheduled",
        "See all",
        expanded = expanded,
        onToggleExpanded = { state.toggleBudgetSection(sectionKey) },
        onClick = { navigation.open(AppRoute.SCHEDULES) },
    ) {
        schedules.take(3).forEachIndexed { index, schedule ->
            if (index > 0) DividerLine()
            ListRow(
                schedule.name,
                listOfNotNull(formatLiveDate(schedule.nextDate), schedule.accountName).joinToString(" · "),
                schedule.amountLabel(state),
                leading = RowLeading.Icon(Lucide.CalendarDays),
            ) { navigation.open(AppRoute.SCHEDULES) }
        }
    }
}

@Composable
internal fun UpcomingSchedulesScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    schedules: List<LiveScheduleSummary>,
    accounts: List<LiveAccountSummary>,
    payeeRecords: List<BudgetRecordEntity>,
) {
    var selectedScheduleId by rememberSaveable(state.selectedBudgetId) { mutableStateOf<String?>(null) }
    var dialogMode by rememberSaveable(state.selectedBudgetId, selectedScheduleId) { mutableStateOf("menu") }
    var nameDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(schedules.firstOrNull { it.id == selectedScheduleId }?.name.orEmpty())
    }
    var amountDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        val schedule = schedules.firstOrNull { it.id == selectedScheduleId }
        mutableStateOf(state.formatAmount(schedule?.amountMinor ?: schedule?.amountRangeMinor?.first ?: 0L))
    }
    var amountMaxDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        val range = schedules.firstOrNull { it.id == selectedScheduleId }?.amountRangeMinor
        mutableStateOf(range?.second?.let(state::formatAmount).orEmpty())
    }
    var amountRuleDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(schedules.firstOrNull { it.id == selectedScheduleId }?.amountOp ?: "is")
    }
    var accountIdDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(schedules.firstOrNull { it.id == selectedScheduleId }?.accountId.orEmpty())
    }
    var payeeIdDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(schedules.firstOrNull { it.id == selectedScheduleId }?.payeeId.orEmpty())
    }
    val selectedSchedule = schedules.firstOrNull { it.id == selectedScheduleId }
    val dateValue =
        remember(selectedSchedule?.id, selectedSchedule?.dateValueJson) {
            selectedSchedule?.dateValueJson?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
        }
    val dateConfig = dateValue as? JsonObject
    var dateDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(
            dateConfig?.get("start")?.jsonPrimitive?.contentOrNull
                ?: (dateValue as? JsonPrimitive)?.contentOrNull
                ?: selectedSchedule?.nextDate.orEmpty(),
        )
    }
    var frequencyDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(dateConfig?.get("frequency")?.jsonPrimitive?.contentOrNull ?: "once")
    }
    var intervalDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(dateConfig?.get("interval")?.jsonPrimitive?.contentOrNull ?: "1")
    }
    var skipWeekendDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(dateConfig?.get("skipWeekend")?.jsonPrimitive?.booleanOrNull ?: false)
    }
    var endModeDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(dateConfig?.get("endMode")?.jsonPrimitive?.contentOrNull ?: "never")
    }
    var endOccurrencesDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(dateConfig?.get("endOccurrences")?.jsonPrimitive?.contentOrNull ?: "12")
    }
    var endDateDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(dateConfig?.get("endDate")?.jsonPrimitive?.contentOrNull ?: selectedSchedule?.nextDate.orEmpty())
    }
    var datePickerTarget by rememberSaveable(state.selectedBudgetId, selectedScheduleId) { mutableStateOf<String?>(null) }
    var editError by rememberSaveable(state.selectedBudgetId, selectedScheduleId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val payeeOptions =
        remember(payeeRecords, selectedSchedule?.payeeId) {
            val names =
                payeeRecords
                    .mapNotNull { record ->
                        record.recordName()?.let { record.recordId to it }
                    }.toMap()
                    .toMutableMap()
            selectedSchedule?.payeeId?.let { id -> if (id !in names) names[id] = id }
            names.entries.sortedBy { it.value.lowercase() }.map { it.key to it.value }
        }
    val accountOptions =
        remember(accounts) {
            accounts.filterNot(LiveAccountSummary::closed).sortedBy { it.name.lowercase() }.map { it.id to it.name }
        }
    PageLazyColumn {
        item(key = "upcoming-schedules-heading") { Heading("Upcoming bills") }
        if (schedules.isEmpty()) {
            item(key = "upcoming-schedules-empty") {
                SurfaceCard {
                    EmptyState(
                        "No upcoming bills",
                        "Schedules added in Actual will appear here.",
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    )
                }
            }
        } else {
            items(schedules.size, key = { index -> schedules[index].id }) { index ->
                val schedule = schedules[index]
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ListRow(
                            schedule.name,
                            listOfNotNull(formatLiveDate(schedule.nextDate), schedule.accountName).joinToString(" · "),
                            schedule.amountLabel(state),
                            leading = RowLeading.Icon(Lucide.CalendarDays),
                            onClick = {
                                selectedScheduleId = schedule.id
                                dialogMode = "menu"
                                mobileSession.clearBudgetActionMessage()
                            },
                        )
                        TandemIconButton(
                            Lucide.ChevronRight,
                            "Schedule actions",
                            modifier = Modifier.padding(end = TandemSpacing.small),
                            sizeDp = 40,
                            iconSizeDp = 18,
                            containerColor = tandemPalette.raised,
                        ) {
                            selectedScheduleId = schedule.id
                            dialogMode = "menu"
                            mobileSession.clearBudgetActionMessage()
                        }
                    }
                }
            }
        }
    }

    if (selectedSchedule != null) {
        Dialog(
            onDismissRequest = {
                if (!mobileSession.isPerformingBudgetAction) {
                    selectedScheduleId = null
                    dialogMode = "menu"
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .padding(horizontal = TandemSpacing.large),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(TandemSpacing.large)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Icon(
                            if (dialogMode == "edit") Lucide.Pencil else Lucide.CalendarDays,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = tandemPalette.accent,
                        )
                        Text(
                            when {
                                dialogMode == "delete" -> "Delete schedule?"
                                dialogMode == "edit" -> "Edit schedule"
                                else -> selectedSchedule.name
                            },
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            containerColor = tandemPalette.raised,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) {
                            selectedScheduleId = null
                            dialogMode = "menu"
                        }
                    }
                    when {
                        dialogMode == "delete" -> {
                            TandemActionButton(
                                "Delete",
                                TandemActionButtonTone.DESTRUCTIVE,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Trash2,
                                enabled = !mobileSession.isPerformingBudgetAction,
                                loading = mobileSession.isPerformingBudgetAction,
                            ) {
                                scope.launch {
                                    val deleted =
                                        mobileSession.manageBudgetSchedule(
                                            state.selectedBudgetId,
                                            selectedSchedule.id,
                                            "delete",
                                            null,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    if (deleted) selectedScheduleId = null
                                }
                            }
                            TandemActionButton(
                                "Keep schedule",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) { dialogMode = "menu" }
                        }
                        dialogMode == "edit" -> {
                            TextFieldCard("Name", nameDraft, { nameDraft = it })
                            TextFieldCard(
                                "Amount",
                                amountDraft,
                                { amountDraft = it },
                                TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)),
                            )
                            ScheduleChoiceField(
                                "Amount rule",
                                when (amountRuleDraft) {
                                    "isapprox" -> "About this amount"
                                    "isbetween" -> "Between two amounts"
                                    else -> "Exact amount"
                                },
                                listOf("is" to "Exact amount", "isapprox" to "About this amount", "isbetween" to "Between two amounts"),
                            ) { amountRuleDraft = it }
                            if (amountRuleDraft == "isbetween") {
                                TextFieldCard(
                                    "Maximum amount",
                                    amountMaxDraft,
                                    { amountMaxDraft = it },
                                    TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)),
                                )
                            }
                            ScheduleChoiceField(
                                "Account",
                                accountOptions.firstOrNull { it.first == accountIdDraft }?.second ?: "Choose account",
                                accountOptions,
                            ) { accountIdDraft = it }
                            ScheduleChoiceField(
                                "Payee",
                                payeeOptions.firstOrNull { it.first == payeeIdDraft }?.second ?: "Choose payee",
                                payeeOptions,
                            ) { payeeIdDraft = it }
                            ScheduleChoiceField(
                                if (frequencyDraft == "once") "Repeats" else "Repeats every",
                                when (frequencyDraft) {
                                    "daily" -> "Daily"
                                    "weekly" -> "Weekly"
                                    "monthly" -> "Monthly"
                                    "yearly" -> "Yearly"
                                    else -> "One time"
                                },
                                listOf(
                                    "once" to "One time",
                                    "daily" to "Daily",
                                    "weekly" to "Weekly",
                                    "monthly" to "Monthly",
                                    "yearly" to "Yearly",
                                ),
                            ) { frequencyDraft = it }
                            if (frequencyDraft != "once") {
                                TextFieldCard(
                                    "Interval",
                                    intervalDraft,
                                    { intervalDraft = it.filter(Char::isDigit).take(3) },
                                    TextFieldOptions(
                                        placeholder = "1",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    ),
                                )
                            }
                            FieldFrame(
                                if (frequencyDraft == "once") "Date" else "Start date",
                                onClickLabel = "Choose date",
                                onClick = { datePickerTarget = "start" },
                            ) {
                                Text(
                                    formatLiveDate(dateDraft),
                                    Modifier.weight(1f),
                                    color = tandemPalette.text,
                                    fontSize = TandemTypography.action,
                                )
                                Icon(
                                    Lucide.ChevronDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = tandemPalette.muted,
                                )
                            }
                            if (frequencyDraft != "once") {
                                ToggleRow("Skip weekends", checked = skipWeekendDraft) { skipWeekendDraft = it }
                                ScheduleChoiceField(
                                    "Ends",
                                    when (endModeDraft) {
                                        "after_n_occurrences" -> "After a number of repeats"
                                        "on_date" -> "On a date"
                                        else -> "Never"
                                    },
                                    listOf(
                                        "never" to "Never",
                                        "after_n_occurrences" to "After a number of repeats",
                                        "on_date" to "On a date",
                                    ),
                                ) { endModeDraft = it }
                                if (endModeDraft == "after_n_occurrences") {
                                    TextFieldCard(
                                        "Number of repeats",
                                        endOccurrencesDraft,
                                        { endOccurrencesDraft = it.filter(Char::isDigit).take(6) },
                                        TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)),
                                    )
                                }
                                if (endModeDraft == "on_date") {
                                    FieldFrame(
                                        "End date",
                                        onClickLabel = "Choose end date",
                                        onClick = { datePickerTarget = "end" },
                                    ) {
                                        Text(
                                            formatLiveDate(endDateDraft),
                                            Modifier.weight(1f),
                                            color = tandemPalette.text,
                                            fontSize = TandemTypography.action,
                                        )
                                        Icon(
                                            Lucide.ChevronDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = tandemPalette.muted,
                                        )
                                    }
                                }
                            }
                            editError?.let {
                                InlineMessage(it, InlineMessageKind.ERROR)
                            }
                            mobileSession.budgetActionMessage?.let {
                                InlineMessage(it, InlineMessageKind.ERROR)
                            }
                            TandemActionButton(
                                if (mobileSession.isPerformingBudgetAction) {
                                    "Saving…"
                                } else {
                                    "Save schedule"
                                },
                                TandemActionButtonTone.PRIMARY,
                                modifier = Modifier.fillMaxWidth(),
                                enabled =
                                    !mobileSession.isPerformingBudgetAction &&
                                        nameDraft.isNotBlank() &&
                                        accountIdDraft.isNotBlank() &&
                                        payeeIdDraft.isNotBlank(),
                                loading = mobileSession.isPerformingBudgetAction,
                            ) {
                                val amountMinor = state.parseAmountMinorUnits(amountDraft)
                                val amountMaxMinor = state.parseAmountMinorUnits(amountMaxDraft)
                                val interval = intervalDraft.toIntOrNull()
                                val occurrences = endOccurrencesDraft.toIntOrNull()
                                val dateIsValid = parseIsoDate(dateDraft) != null
                                val endDateIsValid = endModeDraft != "on_date" || parseIsoDate(endDateDraft) != null
                                val amountRangeIsInvalid =
                                    amountRuleDraft == "isbetween" &&
                                        amountMinor != null &&
                                        amountMaxMinor != null &&
                                        amountMinor > amountMaxMinor
                                val intervalIsInvalid =
                                    frequencyDraft != "once" && (interval == null || interval !in 1..999)
                                val repeatCountIsInvalid =
                                    frequencyDraft != "once" &&
                                        endModeDraft == "after_n_occurrences" &&
                                        (occurrences == null || occurrences !in 1..100_000)
                                val endBeforeStart = endModeDraft == "on_date" && endDateDraft < dateDraft
                                editError =
                                    when {
                                        amountMinor == null -> "Enter a valid amount."
                                        amountRuleDraft == "isbetween" && amountMaxMinor == null ->
                                            "Enter a valid maximum amount."
                                        amountRangeIsInvalid -> "The minimum must not exceed the maximum."
                                        intervalIsInvalid -> "Repeat interval must be between 1 and 999."
                                        repeatCountIsInvalid -> "Enter a valid number of repeats."
                                        !dateIsValid -> "Choose a valid start date."
                                        !endDateIsValid -> "Choose a valid end date."
                                        endBeforeStart -> "The end date must be on or after the start date."
                                        else -> null
                                    }
                                if (editError == null) {
                                    val editedDate =
                                        if (frequencyDraft == "once") {
                                            JsonPrimitive(dateDraft) as JsonElement
                                        } else {
                                            buildJsonObject {
                                                dateConfig?.forEach { (key, value) ->
                                                    if (key !in
                                                        setOf(
                                                            "frequency",
                                                            "interval",
                                                            "start",
                                                            "skipWeekend",
                                                            "endMode",
                                                            "endOccurrences",
                                                            "endDate",
                                                        )
                                                    ) {
                                                        put(key, value)
                                                    }
                                                }
                                                put("frequency", frequencyDraft)
                                                put("interval", interval ?: 1)
                                                put("start", dateDraft)
                                                put("skipWeekend", skipWeekendDraft)
                                                put("endMode", endModeDraft)
                                                if (endModeDraft == "after_n_occurrences") put("endOccurrences", occurrences ?: 1)
                                                if (endModeDraft == "on_date") put("endDate", endDateDraft)
                                            }
                                        }
                                    val fields =
                                        buildJsonObject {
                                            put("name", nameDraft.trim())
                                            put("accountId", accountIdDraft)
                                            put("payeeId", payeeIdDraft)
                                            put("amountOp", amountRuleDraft)
                                            if (amountRuleDraft == "isbetween") {
                                                put(
                                                    "amountRangeMinor",
                                                    buildJsonObject {
                                                        put("num1", amountMinor ?: 0L)
                                                        put("num2", amountMaxMinor ?: 0L)
                                                    },
                                                )
                                            } else {
                                                put("amountMinor", amountMinor ?: 0L)
                                            }
                                            put("date", editedDate)
                                        }
                                    scope.launch {
                                        val saved =
                                            mobileSession.manageBudgetSchedule(
                                                state.selectedBudgetId,
                                                selectedSchedule.id,
                                                "edit",
                                                null,
                                                state.encryptedPassword.takeIf { it.isNotBlank() },
                                                fields,
                                            )
                                        if (saved) selectedScheduleId = null
                                    }
                                }
                            }
                            TandemActionButton(
                                "Cancel",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) { dialogMode = "menu" }
                        }
                        else -> {
                            mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                            TandemActionButton(
                                "Post now",
                                TandemActionButtonTone.PRIMARY,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.CalendarDays,
                                enabled = !mobileSession.isPerformingBudgetAction,
                                loading = mobileSession.isPerformingBudgetAction,
                            ) {
                                scope.launch {
                                    val posted =
                                        mobileSession.manageBudgetSchedule(
                                            state.selectedBudgetId,
                                            selectedSchedule.id,
                                            "post",
                                            null,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    if (posted) selectedScheduleId = null
                                }
                            }
                            if (selectedSchedule.canSkip) {
                                TandemActionButton(
                                    "Skip next date",
                                    TandemActionButtonTone.NEUTRAL,
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = Lucide.CalendarDays,
                                    enabled = !mobileSession.isPerformingBudgetAction,
                                    loading = mobileSession.isPerformingBudgetAction,
                                ) {
                                    scope.launch {
                                        val skipped =
                                            mobileSession.manageBudgetSchedule(
                                                state.selectedBudgetId,
                                                selectedSchedule.id,
                                                "skip",
                                                null,
                                                state.encryptedPassword.takeIf { it.isNotBlank() },
                                            )
                                        if (skipped) selectedScheduleId = null
                                    }
                                }
                            }
                            TandemActionButton(
                                "Edit schedule",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Pencil,
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) {
                                mobileSession.clearBudgetActionMessage()
                                dialogMode = "edit"
                            }
                            TandemActionButton(
                                "Delete",
                                TandemActionButtonTone.DESTRUCTIVE,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Trash2,
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) { dialogMode = "delete" }
                        }
                    }
                }
            }
        }
    }
    if (datePickerTarget != null) {
        ScheduleDatePickerDialog(
            initialDate = if (datePickerTarget == "end") endDateDraft else dateDraft,
            onDismiss = { datePickerTarget = null },
            onSelect = { value ->
                if (datePickerTarget == "end") endDateDraft = value else dateDraft = value
                datePickerTarget = null
            },
        )
    }
}

@Composable
internal fun ScheduleChoiceField(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    FieldFrame(label, onClickLabel = "Choose $label", onClick = { expanded = true }) {
        Text(
            selectedLabel,
            Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.action,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = tandemPalette.muted)
    }
    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(horizontal = TandemSpacing.large)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(TandemSpacing.medium)) {
                    options.forEach { (id, name) ->
                        ListRow(
                            name,
                            trailing = if (name == selectedLabel) "✓" else null,
                            onClick = {
                                onSelect(id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScheduleDatePickerDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val selectedDate = parseIsoDate(initialDate) ?: parseIsoDate(platformTodayIsoDate()) ?: Triple(2026, 1, 1)
    var visibleYear by rememberSaveable(initialDate) { mutableStateOf(selectedDate.first) }
    var visibleMonth by rememberSaveable(initialDate) { mutableStateOf(selectedDate.second) }
    val firstDayOffset = (platformWeekday(visibleYear, visibleMonth, 1) - platformFirstWeekday() + 7) % 7
    val cells = List<Int?>(firstDayOffset) { null } + (1..daysInMonth(visibleYear, visibleMonth)).map { it }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TandemIconButton(Lucide.ChevronLeft, "Previous month", sizeDp = 40, iconSizeDp = 18) {
                        if (visibleMonth == 1) {
                            visibleMonth = 12
                            visibleYear -= 1
                        } else {
                            visibleMonth -= 1
                        }
                    }
                    Text(
                        formatMonth(visibleYear, visibleMonth),
                        Modifier.weight(1f),
                        color = tandemPalette.text,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TandemIconButton(Lucide.ChevronRight, "Next month", sizeDp = 40, iconSizeDp = 18) {
                        if (visibleMonth == 12) {
                            visibleMonth = 1
                            visibleYear += 1
                        } else {
                            visibleMonth += 1
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    (0..6).forEach { column ->
                        val weekday = formatWeekday(visibleYear, visibleMonth, 1 + ((column - firstDayOffset + 7) % 7))
                        Text(
                            weekday.take(1),
                            Modifier.weight(1f),
                            color = tandemPalette.muted,
                            textAlign = TextAlign.Center,
                            fontSize = TandemTypography.caption,
                        )
                    }
                }
                cells.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                        week.forEach { day ->
                            if (day == null) {
                                Spacer(Modifier.weight(1f).height(44.dp))
                            } else {
                                val value = transactionIsoDate(visibleYear, visibleMonth, day)
                                val isSelected = value == initialDate
                                SelectableTile(
                                    selected = isSelected,
                                    contentDescription = displayTransactionDate(value),
                                    onSelect = { onSelect(value) },
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                    shape = CircleShape,
                                    selectedBackground = tandemPalette.accentSurface,
                                    unselectedBackground = Color.Transparent,
                                ) {
                                    Text(
                                        day.toString(),
                                        color = if (isSelected) tandemPalette.accent else tandemPalette.text,
                                        fontSize = TandemTypography.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(44.dp)) }
                    }
                }
                TandemActionButton("Cancel", TandemActionButtonTone.NEUTRAL, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
            }
        }
    }
}
