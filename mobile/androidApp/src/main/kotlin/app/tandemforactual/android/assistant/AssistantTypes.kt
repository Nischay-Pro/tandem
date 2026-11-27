package app.tandemforactual.android.assistant

import androidx.appfunctions.AppFunctionSerializable
import app.tandemforactual.ui.core.automation.AutomationAmountFormat
import app.tandemforactual.ui.core.automation.AutomationErrorCode
import app.tandemforactual.ui.core.automation.AutomationException
import app.tandemforactual.ui.core.automation.AutomationTransactionInput

/**
 * A transaction to log.
 *
 * @property amount The amount as a decimal number with a dot, such as -12.40. Start it with - for money spent or + for
 *   money received, or leave out the sign and set direction.
 * @property direction "out" for money spent or "in" for money received, when the amount has no sign.
 * @property account The account's name, such as Checking. It must already exist in the budget.
 * @property payee Who was paid or who paid, such as Corner Café. A new name becomes a new payee.
 * @property category The category's name, such as Groceries. An unknown name leaves the transaction uncategorized.
 * @property date The date as year-month-day, such as 2026-11-27. Leave it out for today.
 * @property notes Notes to keep with the transaction.
 * @property cleared Whether the transaction has already cleared the bank.
 * @property externalId An ID from the source, such as a notification's ID. Logging the same ID again within 30 days
 *   adds nothing.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantTransaction(
    val amount: String,
    val direction: String? = null,
    val account: String,
    val payee: String? = null,
    val category: String? = null,
    val date: String? = null,
    val notes: String? = null,
    val cleared: Boolean = false,
    val externalId: String? = null,
) {
    internal fun toAutomation() =
        AutomationTransactionInput(
            amount = signedAmount(),
            payee = payee,
            category = category,
            account = account,
            date = date,
            notes = notes,
            cleared = cleared.toString(),
            externalId = externalId,
            amountFormat = AutomationAmountFormat.PLAIN,
        )

    private fun signedAmount(): String {
        val text = amount.trim()
        val sign =
            when (direction?.trim()?.lowercase()) {
                null, "" -> return text
                "out" -> "-"
                "in" -> "+"
                else -> throw AutomationException(AutomationErrorCode.AMOUNT, "Set direction to out or in.")
            }
        val signed = text.firstOrNull()?.takeIf { it in "+-−" }?.let { if (it == '+') "+" else "-" }
        if (signed != null && signed != sign) {
            throw AutomationException(AutomationErrorCode.AMOUNT, "The amount’s sign and direction disagree.")
        }
        return if (signed == null) sign + text else text
    }
}

/**
 * A logged transaction.
 *
 * @property transactionId The transaction's ID in Tandem.
 * @property summary What was added, such as "Added −€12.40 · Corner Café · Groceries"; empty when nothing new was added.
 * @property repeated Whether the external ID was already used, so nothing new was added.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantLoggedTransaction(
    val transactionId: String,
    val summary: String,
    val repeated: Boolean,
)

/**
 * A category's numbers for one month, as decimal numbers with a dot in the budget's currency.
 *
 * @property amount What is left to spend; negative when the category is over budget.
 * @property budgeted What was budgeted.
 * @property spent What was spent; refunds make it smaller.
 * @property overspent Whether the category is over budget.
 * @property sentence The numbers as a sentence to say or show, such as "€42.10 left for Groceries."
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantLeftToSpend(
    val amount: String,
    val budgeted: String,
    val spent: String,
    val overspent: Boolean,
    val sentence: String,
)
