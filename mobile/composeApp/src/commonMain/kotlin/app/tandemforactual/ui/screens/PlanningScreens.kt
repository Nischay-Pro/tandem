@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.DebtKind
import app.tandemforactual.data.planning.DebtTerms
import app.tandemforactual.data.planning.PlanningMath
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.FilterPills
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemChoiceDialog
import app.tandemforactual.ui.components.TandemModalHeader
import app.tandemforactual.ui.components.TandemTaskSheet
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private fun BudgetDisplaySettings?.amountOptions() =
    TextFieldOptions(
        placeholder = "0",
        currencySymbol = this?.currencySymbol ?: "$",
        currencySymbolAfter = this?.currencySymbolPosition == "after",
        currencySymbolSpace = this?.currencySpaceBetweenAmountAndSymbol == true,
        amountFractionDigits = this?.currencyFractionDigits ?: 2,
    )

/** Parses a percentage such as "19.99" into basis points exactly, without floating point. */
internal fun parseRateBasisPoints(value: String): Int? {
    val text = value.trim().removeSuffix("%").trim()
    if (!Regex("^\\d{1,3}(\\.\\d{1,2})?$").matches(text)) return null
    val (whole, fraction) = (text.split('.') + "").let { it[0] to it[1].padEnd(2, '0') }
    return (whole.toInt() * 100 + fraction.toInt()).takeIf { it <= 10_000 }
}

internal fun formatRateBasisPoints(basisPoints: Int): String = "${basisPoints / 100}.${(basisPoints % 100).toString().padStart(2, '0')}%"

/** How much a goal has: its linked category's balance, else its linked account's balance, else unknown. */
internal fun SavingsGoal.saved(
    month: LiveBudgetMonthSummary?,
    accounts: List<LiveAccountSummary>,
): Long? =
    (
        categoryId?.let { id ->
            month
                ?.groups
                ?.flatMap { it.categories }
                ?.firstOrNull { it.id == id }
                ?.balanceMinor
        }
            ?: accountId?.let { id -> accounts.firstOrNull { it.id == id }?.balanceMinor }
    )?.coerceAtLeast(0)

@Composable
internal fun SavingsGoalsCard(
    goals: List<SavingsGoal>,
    month: LiveBudgetMonthSummary?,
    accounts: List<LiveAccountSummary>,
    settings: BudgetDisplaySettings?,
    today: String,
    privacy: Boolean,
    onEdit: (SavingsGoal?) -> Unit,
) {
    val palette = tandemPalette
    SectionCard("Savings goals", "Progress toward what you’re saving for", "Add goal", onClick = { onEdit(null) }) {
        goals.forEachIndexed { index, goal ->
            if (index > 0) DividerLine()
            val saved = goal.saved(month, accounts)
            val fraction = if (saved == null || goal.targetMinor <= 0) 0f else (saved.toFloat() / goal.targetMinor).coerceIn(0f, 1f)
            val progress by animateFloatAsState(
                fraction,
                tandemMotionSpec(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
                label = "goal-progress",
            )
            val detail =
                when {
                    saved == null -> "Link a category or account to track progress"
                    privacy -> "${(fraction * 100).toInt()}% saved"
                    goal.targetDate != null && saved < goal.targetMinor -> {
                        val monthly =
                            PlanningMath.monthlyToTarget(
                                goal.targetMinor,
                                saved,
                                PlanningMath.monthsBetween(today, goal.targetDate.orEmpty()),
                            )
                        "${formatCurrency(
                            saved,
                            settings,
                        )} of ${formatCurrency(goal.targetMinor, settings)} · about ${formatCurrency(monthly, settings)} a month"
                    }
                    else -> "${formatCurrency(saved, settings)} of ${formatCurrency(goal.targetMinor, settings)}"
                }
            Column(
                Modifier.fillMaxWidth().padding(bottom = TandemSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ListRow(
                    goal.name,
                    detail,
                    leading = RowLeading.Icon(Lucide.PiggyBank),
                    onClick = { onEdit(goal) },
                    options = ListRowOptions(dense = true),
                )
                if (saved != null) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.medium)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(palette.raised),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .clip(CircleShape)
                                    .background(palette.accent),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DebtOverviewCard(
    debts: List<LiveAccountSummary>,
    terms: List<DebtTerms>,
    settings: BudgetDisplaySettings?,
    privacy: Boolean,
    money: @Composable (Long) -> Unit,
    onEdit: (LiveAccountSummary) -> Unit,
) {
    SectionCard("Debt overview", "Balances owed across your accounts") {
        debts.forEachIndexed { index, account ->
            if (index > 0) DividerLine()
            val owed = -(account.balanceMinor ?: 0)
            val term = terms.firstOrNull { it.accountId == account.id }
            val apr = term?.aprBasisPoints
            val minimum = term?.minimumPaymentMinor
            val detail =
                when {
                    apr == null || minimum == null -> "Add the APR and minimum payment to estimate payoff"
                    privacy -> "${formatRateBasisPoints(apr)} APR"
                    else ->
                        PlanningMath.payoff(owed, apr, minimum)?.let {
                            "Estimate: ${it.months} months at the minimum, ${formatCurrency(it.totalInterestMinor, settings)} interest"
                        } ?: "The minimum doesn’t cover the interest at ${formatRateBasisPoints(apr)} APR"
                }
            ListRow(account.name, detail, leading = RowLeading.Icon(Lucide.CreditCard), trailingContent = {
                money(owed)
            }, onClick = { onEdit(account) })
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun SavingsGoalSheet(
    goal: SavingsGoal?,
    visible: Boolean,
    month: LiveBudgetMonthSummary?,
    settings: BudgetDisplaySettings?,
    onSave: (SavingsGoal) -> Unit,
    onDelete: (SavingsGoal) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(goal, visible) { mutableStateOf(goal?.name.orEmpty()) }
    var target by remember(goal, visible) { mutableStateOf(goal?.targetMinor?.let { formatAmount(it, settings) }.orEmpty()) }
    var date by remember(goal, visible) { mutableStateOf(goal?.targetDate.orEmpty()) }
    var categoryId by remember(goal, visible) { mutableStateOf(goal?.categoryId) }
    var choosingCategory by remember { mutableStateOf(false) }
    var error by remember(visible) { mutableStateOf<String?>(null) }
    val categories = month?.groups.orEmpty().flatMap { it.categories }
    TandemTaskSheet(visible = visible, onDismissRequest = onDismiss, onDismissed = {}, secure = true) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.fillMaxWidth().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader(if (goal == null) "New savings goal" else "Savings goal", onClose = onDismiss)
                TextFieldCard("Name", name, { name = it.take(60) }, TextFieldOptions(placeholder = "Emergency fund"))
                TextFieldCard("Target", target, { target = it }, settings.amountOptions())
                TextFieldCard("Target date (optional)", date, { date = it.take(10) }, TextFieldOptions(placeholder = "2027-06-01"))
                ListRow(
                    "Track with category",
                    categories.firstOrNull { it.id == categoryId }?.name ?: "None",
                    onClick = { choosingCategory = true },
                )
                error?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    if (goal != null) {
                        TandemActionButton("Delete", TandemActionButtonTone.DESTRUCTIVE, Modifier.weight(1f)) { onDelete(goal) }
                    }
                    TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.weight(1f)) {
                        val amount = parseAmountMinorUnits(target, settings)
                        error =
                            when {
                                name.isBlank() -> "Enter a name."
                                amount == null || amount <= 0 -> "Enter a target amount."
                                date.isNotBlank() && !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date) -> "Use a date like 2027-06-01."
                                else -> null
                            }
                        if (error == null && amount != null) {
                            onSave(
                                SavingsGoal(
                                    id = goal?.id ?: Uuid.random().toString(),
                                    name = name.trim(),
                                    targetMinor = amount,
                                    currency = settings?.currencyCode.orEmpty(),
                                    targetDate = date.ifBlank { null },
                                    categoryId = categoryId,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
    TandemChoiceDialog(choosingCategory, onDismissRequest = { choosingCategory = false }) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 400.dp).padding(horizontal = TandemSpacing.large)) {
            ListRow("None", onClick = {
                categoryId = null
                choosingCategory = false
            })
            categories.forEach { category ->
                DividerLine()
                ListRow(category.name, onClick = {
                    categoryId = category.id
                    choosingCategory = false
                })
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun DebtTermsSheet(
    account: LiveAccountSummary?,
    terms: DebtTerms?,
    settings: BudgetDisplaySettings?,
    onSave: (DebtTerms) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = tandemPalette
    var apr by remember(account) { mutableStateOf(terms?.aprBasisPoints?.let { formatRateBasisPoints(it).removeSuffix("%") }.orEmpty()) }
    var minimum by remember(account) { mutableStateOf(terms?.minimumPaymentMinor?.let { formatAmount(it, settings) }.orEmpty()) }
    var dueDay by remember(account) { mutableStateOf(terms?.dueDay?.toString().orEmpty()) }
    var kind by remember(account) { mutableStateOf(terms?.kind ?: DebtKind.UNSPECIFIED) }
    var paysInFull by remember(account) { mutableStateOf(terms?.paysInFull == true) }
    var error by remember(account) { mutableStateOf<String?>(null) }
    TandemTaskSheet(visible = account != null, onDismissRequest = onDismiss, onDismissed = {}) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                TandemModalHeader(account?.name.orEmpty(), onClose = onDismiss)
                Text(
                    "Used only on this device to estimate payoff. Leave anything you don’t know blank.",
                    color = palette.muted,
                    fontSize = TandemTypography.compactBody,
                )
                FilterPills(
                    listOf(DebtKind.CARD to "Credit card", DebtKind.LOAN to "Loan", DebtKind.NOT_DEBT to "Not a debt"),
                    kind,
                    { kind = it },
                )
                if (kind == DebtKind.CARD) {
                    FilterPills(
                        listOf(false to "I carry a balance", true to "I pay it in full each month"),
                        paysInFull,
                        { paysInFull = it },
                    )
                }
                TextFieldCard("APR (%)", apr, { apr = it.take(6) }, TextFieldOptions(placeholder = "19.99"))
                TextFieldCard("Minimum payment", minimum, { minimum = it }, settings.amountOptions())
                TextFieldCard(
                    "Due day of the month",
                    dueDay,
                    { dueDay = it.filter(Char::isDigit).take(2) },
                    TextFieldOptions(placeholder = "12"),
                )
                error?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth()) {
                    val rate = apr.takeIf { it.isNotBlank() }?.let(::parseRateBasisPoints)
                    val payment = minimum.takeIf { it.isNotBlank() }?.let { parseAmountMinorUnits(it, settings) }
                    val day = dueDay.toIntOrNull()
                    error =
                        when {
                            apr.isNotBlank() && rate == null -> "Enter the APR as a percentage, like 19.99."
                            minimum.isNotBlank() && (payment == null || payment < 0) -> "Enter the minimum payment."
                            dueDay.isNotBlank() && (day == null || day !in 1..31) -> "Enter a day between 1 and 31."
                            else -> null
                        }
                    if (error == null && account != null) {
                        onSave(
                            DebtTerms(
                                terms?.id ?: Uuid.random().toString(),
                                account.id,
                                rate,
                                payment,
                                day,
                                kind = kind,
                                paysInFull = kind == DebtKind.CARD && paysInFull,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Opens the planning sheets from Home and saves through the session. */
@Composable
internal fun rememberPlanningEditors(
    state: AppState,
    mobileSession: TandemMobileSession,
    month: LiveBudgetMonthSummary?,
): PlanningEditors {
    val scope = rememberCoroutineScope()
    val editors = remember { PlanningEditors() }
    val settings = mobileSession.budgetDisplaySettings
    SavingsGoalSheet(
        editors.goal,
        editors.goalVisible,
        month,
        settings,
        onSave = { goal ->
            scope.launch { mobileSession.saveSavingsGoal(state.selectedBudgetId, goal) }
            editors.goalVisible = false
        },
        onDelete = { goal ->
            scope.launch { mobileSession.deleteSavingsGoal(state.selectedBudgetId, goal.id) }
            editors.goalVisible = false
        },
        onDismiss = { editors.goalVisible = false },
    )
    DebtTermsSheet(
        editors.debtAccount,
        mobileSession.debtTerms.firstOrNull { it.accountId == editors.debtAccount?.id },
        settings,
        onSave = { terms ->
            scope.launch { mobileSession.saveDebtTerms(state.selectedBudgetId, terms) }
            editors.debtAccount = null
        },
        onDismiss = { editors.debtAccount = null },
    )
    return editors
}

internal class PlanningEditors {
    var goal by mutableStateOf<SavingsGoal?>(null)
    var goalVisible by mutableStateOf(false)
    var debtAccount by mutableStateOf<LiveAccountSummary?>(null)

    fun editGoal(goal: SavingsGoal?) {
        this.goal = goal
        goalVisible = true
    }
}

/** Shows this device's planning recovery key, or takes the one from a device that already shares planning records. */
@Composable
internal fun RecoveryKeySheet(
    visible: Boolean,
    recoveryKey: String,
    onUseKey: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = tandemPalette
    var entered by remember(visible) { mutableStateOf("") }
    var error by remember(visible) { mutableStateOf<String?>(null) }
    TandemTaskSheet(visible = visible, onDismissRequest = onDismiss, onDismissed = {}) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.fillMaxWidth().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader("Planning recovery key", onClose = onDismiss)
                Text(
                    "Planning details like savings goals are encrypted before they sync. To add a device, enter this key there. Keep it somewhere safe.",
                    color = palette.muted,
                    fontSize = TandemTypography.compactBody,
                )
                Text(recoveryKey, color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.Medium)
                TextFieldCard(
                    "Key from another device",
                    entered,
                    { entered = it.take(80) },
                    TextFieldOptions(placeholder = "8 groups of 8 characters"),
                )
                error?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                TandemActionButton(
                    "Use this key",
                    TandemActionButtonTone.PRIMARY,
                    Modifier.fillMaxWidth(),
                    enabled = entered.isNotBlank(),
                ) {
                    val key = parseRecoveryKey(entered)
                    if (key == null) error = "Enter all 64 characters of the key." else onUseKey(key)
                }
            }
        }
    }
}

internal fun planningSyncDetail(status: PlanningSyncStatus): Pair<String, String>? =
    when (status) {
        PlanningSyncStatus.OFF -> null
        PlanningSyncStatus.SYNCED -> "Planning details synced" to "Savings goals and debt details match your other devices."
        PlanningSyncStatus.WAITING ->
            "Planning details saved on this device" to
                "They’ll sync when Tandem’s extension service is reachable. Your budget syncs as usual."
        PlanningSyncStatus.NEEDS_RECOVERY_KEY ->
            "Enter your planning recovery key" to
                "Another device already shares planning details for this budget. Enter its recovery key in Settings."
    }
