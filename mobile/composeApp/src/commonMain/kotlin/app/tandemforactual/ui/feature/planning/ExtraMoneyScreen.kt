@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.planning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.planning.AccountMovement
import app.tandemforactual.data.planning.ExtraMoneyGuide
import app.tandemforactual.data.planning.GoalGap
import app.tandemforactual.data.planning.RetirementContributions
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.planning.RetirementRoom
import app.tandemforactual.data.planning.YearMovements
import app.tandemforactual.data.regions.us.retirement.k401.K401
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.ui.saved
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.PageColumns
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing

/** Compares where an extra amount each month would do the most, without moving any money. */
@Composable
internal fun ExtraMoneyScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveAccounts: List<LiveAccountSummary>,
    month: LiveBudgetMonthSummary?,
) {
    if (month == null) {
        PageColumn { EmptyState("Open a budget month first", "The comparison starts from this month’s spending.") }
        return
    }
    val settings = state.budgetDisplaySettings
    val spending = rememberTypicalMonth(state, mobileSession, month).money.spendingMinor
    var draft by rememberSaveable(state.selectedBudgetId) {
        mutableStateOf(formatAmount(WhatIfStart.of(spending, settings?.currencyFractionDigits ?: 2), settings))
    }
    val extra = parseAmountMinorUnits(draft, settings)?.coerceAtLeast(0) ?: 0
    val matchGain = rememberMatchGain(state, mobileSession, extra)
    val comparison =
        ExtraMoneyGuide.compare(
            extraMinor = extra,
            matchGainMinor = matchGain,
            debts = whatIfDebts(liveAccounts, mobileSession.planning.debtTerms),
            cashMinor = liveAccounts.filter { !it.offBudget && !it.closed }.sumOf { (it.balanceMinor ?: 0).coerceAtLeast(0) },
            monthlySpendingMinor = spending,
            goals =
                mobileSession.planning.savingsGoals.map {
                    GoalGap(
                        it.id,
                        it.name,
                        it.targetMinor - (it.saved(month, liveAccounts) ?: 0),
                    )
                },
        )
    PageColumns(
        primary = {
            SectionCard("Extra each month", "Compare what it would do in each place. Nothing here moves money.") {
                Column(Modifier.padding(TandemSpacing.large)) {
                    TextFieldCard(
                        "Amount",
                        draft,
                        { draft = it },
                        TextFieldOptions(
                            placeholder = "0",
                            currencySymbol = settings?.currencySymbol ?: "$",
                            currencySymbolAfter = settings?.currencySymbolPosition == "after",
                            currencySymbolSpace = settings?.currencySpaceBetweenAmountAndSymbol == true,
                            amountFractionDigits = settings?.currencyFractionDigits ?: 2,
                        ),
                    )
                }
            }
            if (comparison.thinBuffer) {
                InlineMessage(
                    "Less than a month of spending is set aside. Many people build a small cushion before paying debt early or investing.",
                    InlineMessageKind.INFO,
                )
            }
        },
        secondary = { ExtraMoneyOptions(comparison, { state.formatCurrency(it) }, navigation.open) },
    )
}

/** A starting amount: about 5% of the month's spending, rounded to an amount people would type. */
private object WhatIfStart {
    fun of(
        spendingMinor: Long,
        fractionDigits: Int,
    ): Long {
        val unit = (1..fractionDigits).fold(1L) { acc, _ -> acc * 10 }
        val major = (spendingMinor / unit / 20).coerceAtLeast(1)
        var magnitude = 1L
        while (magnitude * 10 <= major) magnitude *= 10
        return (major / magnitude) * magnitude * unit
    }
}

/** Employer match gained this year by adding [extraMinor] a month to each 401(k) with a match set up; null without one. */
@Composable
private fun rememberMatchGain(
    state: AppState,
    session: TandemMobileSession,
    extraMinor: Long,
): Long? {
    val profiles = session.planning.retirementProfiles.filter { it.planId == K401.id && it.match != null && it.salaryMinor != null }
    var movements by remember(state.selectedBudgetId) { mutableStateOf<Map<String, List<AccountMovement>>>(emptyMap()) }
    val planAccounts =
        session.planning.retirementProfiles
            .mapNotNull { it.accountId }
            .toSet()
    val accountIds = profiles.mapNotNull { it.accountId }
    LaunchedEffect(state.selectedBudgetId, accountIds) {
        movements =
            session.insights.loadAccountMovements(state.selectedBudgetId, accountIds, state.encryptedPassword.takeIf { it.isNotBlank() })
    }
    if (profiles.isEmpty()) return null
    val today = platformTodayIsoDate()
    val year = today.take(4).toInt()
    val monthsLeft = 13 - today.substring(5, 7).toInt()
    return profiles.sumOf { profile ->
        val years =
            RetirementContributions.byPlanYear(
                K401,
                movements[profile.accountId].orEmpty(),
                planAccounts - profile.accountId.orEmpty(),
            )
        matchGain(profile, year, years[year] ?: YearMovements(), extraMinor * monthsLeft)
    }
}

private fun matchGain(
    profile: RetirementProfile,
    year: Int,
    totals: YearMovements,
    addedMinor: Long,
): Long {
    val match = profile.match!!
    val salary = profile.salaryMinor!!
    val (yours, _) = RetirementRoom.split(K401, profile, year, totals)
    val age = profile.ageIn(year)
    val now = K401.match(year, age, salary, yours, match).employerMinor
    return K401.match(year, age, salary, yours + addedMinor, match).employerMinor - now
}
