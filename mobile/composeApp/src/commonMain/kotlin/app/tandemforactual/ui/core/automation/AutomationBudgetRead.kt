package app.tandemforactual.ui.core.automation

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.findRecord
import app.tandemforactual.data.store.findRecords
import app.tandemforactual.data.store.readAccountBalance
import app.tandemforactual.data.store.readBudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.longValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.stringValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class AutomationReadInput(
    val category: String? = null,
    val account: String? = null,
    val month: String? = null,
)

/** Budget numbers in minor units, for one category when one was asked for and otherwise for the whole month. */
data class AutomationBudgetNumbers(
    val leftToSpendMinor: Long?,
    val budgetedMinor: Long?,
    val spentMinor: Long?,
    val accountBalanceMinor: Long?,
    val toBudgetMinor: Long?,
    val displaySettings: BudgetDisplaySettings?,
) {
    /** Each number as plain decimal text that automation apps can do maths with, such as "-12.40"; blank when unknown. */
    fun text(minor: Long?): String = minor?.let { automationDecimal(it, displaySettings?.currencyFractionDigits ?: 2) }.orEmpty()

    /** Left to spend as Siri says and shows it, for [categoryName] or, without one, the whole month. */
    fun leftToSpend(categoryName: String?): AutomationLeftToSpend {
        val left = leftToSpendMinor ?: 0L
        val amount = formatCurrency(kotlin.math.abs(left), displaySettings)
        val sentence =
            when {
                categoryName == null && left < 0 -> "This month is $amount over budget."
                categoryName == null -> "$amount left to spend this month."
                left < 0 -> "$categoryName is $amount over budget."
                else -> "$amount left for $categoryName."
            }
        val budgeted = formatCurrency(budgetedMinor ?: 0L, displaySettings)
        val spent = formatCurrency(spentMinor ?: 0L, displaySettings)
        return AutomationLeftToSpend(
            amountText = amount,
            overspent = left < 0,
            sentence = sentence,
            detail = "$budgeted budgeted · $spent spent",
            value = text(left),
        )
    }
}

/** [value] is plain decimal text for Shortcuts to do maths with; [sentence] is what Siri says, such as "Checking has $1,240.00." */
data class AutomationAccountBalance(
    val amountText: String,
    val sentence: String,
    val value: String,
)

/** The account's balance as Siri says and shows it; a balance below zero reads as owed. */
fun AutomationBudgetNumbers.accountBalance(accountName: String): AutomationAccountBalance {
    val balance = accountBalanceMinor ?: 0L
    val amount = formatCurrency(kotlin.math.abs(balance), displaySettings)
    val sentence = if (balance < 0) "$accountName owes $amount." else "$accountName has $amount."
    return AutomationAccountBalance(formatCurrency(balance, displaySettings), sentence, text(balance))
}

/** [value] is plain decimal text for Shortcuts to do maths with; the rest is formatted the way Tandem shows amounts. */
data class AutomationLeftToSpend(
    val amountText: String,
    val overspent: Boolean,
    val sentence: String,
    val detail: String,
    val value: String,
)

private val MONTH = Regex("^[0-9]{4}-(0[1-9]|1[0-2])$")

/** Reads this device's copy of the budget only; nothing is fetched. Spending is positive, refunds make it smaller. */
suspend fun readAutomationBudget(
    database: TandemDatabase,
    budgetId: String,
    input: AutomationReadInput,
    todayIso: String,
): AutomationBudgetNumbers {
    val repository = LocalBudgetRepository(database) { 0L }
    val month = automationInput(input.month) ?: todayIso.take(7)
    if (!MONTH.matches(
            month,
        )
    ) {
        throw AutomationException(AutomationErrorCode.MONTH, "Use a month written as year-month, such as ${todayIso.take(7)}.")
    }
    val body =
        repository.findRecord(budgetId, "budget-month", month)?.recordBody()
            ?: throw AutomationException(
                AutomationErrorCode.NOT_ON_DEVICE,
                "This device has no budget for $month yet. Open Tandem to sync it.",
            )
    val categoryText = automationInput(input.category)
    val scope = categoryText?.let { budgetMonthCategory(body, it) } ?: body
    val leftKey = if (categoryText == null) "totalBalance" else "balance"
    val budgetedKey = if (categoryText == null) "totalBudgeted" else "budgeted"
    val spentKey = if (categoryText == null) "totalSpent" else "spent"
    val balance =
        automationInput(input.account)?.let { text ->
            val account = automationAccount(repository.findRecords(budgetId, "account"), text)
            repository.readAccountBalance(budgetId, account.recordId)
        }
    return AutomationBudgetNumbers(
        leftToSpendMinor = scope.longValue(leftKey),
        budgetedMinor = scope.longValue(budgetedKey),
        spentMinor = scope.longValue(spentKey)?.let { -it },
        accountBalanceMinor = balance,
        toBudgetMinor = body.longValue("toBudget"),
        displaySettings = repository.readBudgetDisplaySettings(budgetId),
    )
}

private fun budgetMonthCategory(
    month: JsonObject,
    text: String,
): JsonObject {
    val categories =
        (month["categoryGroups"] as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .flatMap { group -> (group["categories"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject } }
    return categories.firstOrNull { it.stringValue("id") == text }
        ?: categories.firstOrNull { it.stringValue("name")?.trim().equals(text, ignoreCase = true) }
        ?: throw AutomationException(AutomationErrorCode.CATEGORY, "No category named “$text” in this budget.")
}

/** Hundredths as plain decimal text with a period and no grouping, rounded to the budget's [fractionDigits]. */
fun automationDecimal(
    hundredths: Long,
    fractionDigits: Int,
): String {
    val magnitude = kotlin.math.abs(hundredths)
    val text =
        if (fractionDigits <= 0) {
            ((magnitude + 50) / 100).toString()
        } else {
            "${magnitude / 100}.${(magnitude % 100).toString().padStart(2, '0')}"
        }
    return if (hundredths < 0 && text.any { it in '1'..'9' }) "-$text" else text
}
