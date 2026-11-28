package app.tandemforactual.ui.feature.settings

import app.tandemforactual.data.planning.CustomPaymentFormat
import app.tandemforactual.data.planning.CustomReport
import app.tandemforactual.data.planning.DebtTerms
import app.tandemforactual.data.planning.Reimbursement
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.planning.SavedSearch
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.data.planning.UnsyncedPlanningRecord
import app.tandemforactual.ui.core.format.formatInteger

private val NAMED_KINDS =
    listOf(
        SavingsGoal.TYPE to ("goal" to "goals"),
        SharedBill.TYPE to ("shared bill" to "shared bills"),
        CustomReport.TYPE to ("saved report" to "saved reports"),
        SavedSearch.TYPE to ("saved search" to "saved searches"),
        CustomPaymentFormat.TYPE to ("payment format" to "payment formats"),
        DebtTerms.TYPE to ("debt detail" to "debt details"),
        Reimbursement.TYPE to ("reimbursement" to "reimbursements"),
        RetirementProfile.TYPE to ("retirement plan" to "retirement plans"),
    )

/** What erasing this device loses for good, or null when every planning record is also synced elsewhere. */
internal fun eraseLossSummary(
    records: List<UnsyncedPlanningRecord>,
    openBudgetId: String,
): String? {
    if (records.isEmpty()) return null
    val counts = records.groupingBy { it.recordType }.eachCount()
    val named = NAMED_KINDS.mapNotNull { (type, nouns) -> counts[type]?.let { phrase(it, nouns) } }
    val other = counts.filterKeys { type -> NAMED_KINDS.none { it.first == type } }.values.sum()
    val parts = named + listOfNotNull(other.takeIf { it > 0 }?.let { phrase(it, "other planning detail" to "other planning details") })
    val list = if (parts.size == 1) parts.single() else parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    val elsewhere =
        if (records.any { it.budgetId != openBudgetId }) {
            " Some belong to other budgets; open each one to back it up."
        } else {
            ""
        }
    return "$list haven’t synced anywhere else. Erasing deletes them for good.$elsewhere"
}

private fun phrase(
    count: Int,
    nouns: Pair<String, String>,
): String = "${formatInteger(count)} ${if (count == 1) nouns.first else nouns.second}"
