package app.tandemforactual.android.assistant

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import app.tandemforactual.android.tasker.AutomationCalls
import app.tandemforactual.ui.core.automation.AutomationReadInput

/** What assistants such as Gemini can do in Tandem on Android 16 and later, under the same Settings › Automation rules as Tasker. */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(serviceName = "TandemAppFunctionService", appFunctionXmlFileName = "tandem_app_functions")
abstract class AssistantFunctions : AppFunctionService() {
    /**
     * Logs a transaction in the budget open in Tandem. It syncs to Actual Budget, Actual's rules run on it, and a
     * notification offers Undo. Works only after the user allows automation apps to add transactions in Tandem.
     *
     * @param transaction The amount, account and optional details of the transaction.
     * @return The new transaction, or the earlier one when its external ID was already used.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun logTransaction(transaction: AssistantTransaction): AssistantLoggedTransaction =
        assistantCall {
            val added = AutomationCalls.add(this, transaction.toAutomation(), ASSISTANT_SOURCE)
            AssistantLoggedTransaction(added.transactionId, added.summary.takeUnless { added.repeated }.orEmpty(), added.repeated)
        }

    /**
     * Reads how much is left to spend in a category this month, from this device's copy of the budget open in Tandem.
     * Works only after the user allows automation apps to read balances, and never while Tandem's app lock or Hide
     * budget amounts is on.
     *
     * @param category The category's name, such as Groceries.
     * @param month The month as year-month, such as 2026-11. Leave it out for this month.
     * @return What is left, budgeted and spent in that category.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun leftToSpend(
        category: String,
        month: String? = null,
    ): AssistantLeftToSpend =
        assistantCall {
            val numbers = AutomationCalls.read(this, AutomationReadInput(category = category, month = month))
            val left = numbers.leftToSpend(category.trim())
            AssistantLeftToSpend(
                amount = numbers.text(numbers.leftToSpendMinor),
                budgeted = numbers.text(numbers.budgetedMinor),
                spent = numbers.text(numbers.spentMinor),
                overspent = left.overspent,
                sentence = left.sentence,
            )
        }
}

internal const val ASSISTANT_SOURCE = "Assistant"
