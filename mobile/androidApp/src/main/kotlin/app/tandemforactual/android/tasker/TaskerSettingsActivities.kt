package app.tandemforactual.android.tasker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import app.tandemforactual.ui.core.automation.AutomationAccess
import app.tandemforactual.ui.core.automation.AutomationChoices
import app.tandemforactual.ui.feature.automation.AutomationSetupContent
import app.tandemforactual.ui.feature.automation.AutomationSetupField
import app.tandemforactual.ui.feature.automation.AutomationSetupScreen
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner

private class SetupState(
    val preferences: Map<String, String>,
    val choices: AutomationChoices?,
)

/** Tasker opens this to set up an action; Save hands the settings back to Tasker. */
abstract class TaskerSettingsActivity<I : Any, O : Any, R : TaskerPluginRunner<I, O>> :
    ComponentActivity(),
    TaskerPluginConfig<I> {
    override val context: Context get() = applicationContext
    protected abstract val helper: TaskerPluginConfigHelper<I, O, R>
    protected abstract var input: I

    protected abstract fun content(
        preferences: Map<String, String>,
        choices: AutomationChoices?,
        variables: List<String>,
    ): AutomationSetupContent

    protected abstract fun inputFrom(values: Map<String, String>): I

    override fun assignFromInput(input: TaskerInput<I>) {
        this.input = input.regular
    }

    override val inputForTasker: TaskerInput<I> get() = TaskerInput(input)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        helper.onCreate()
        val variables = helper.relevantVariables?.toList().orEmpty()
        setContent {
            val state by produceState<SetupState?>(null) {
                value = SetupState(AutomationCalls.preferences(applicationContext), AutomationCalls.choices(applicationContext))
            }
            state?.let { loaded ->
                AutomationSetupScreen(loaded.preferences, content(loaded.preferences, loaded.choices, variables), ::finish) { values ->
                    input = inputFrom(values)
                    helper.finishForTasker()
                }
            }
        }
    }
}

private fun lockedNotice(choices: AutomationChoices?): String? =
    if (choices == null) "Open a budget in Tandem to pick from its names. You can still type names or Tasker variables." else null

class TaskerAddTransactionActivity :
    TaskerSettingsActivity<TaskerAddTransactionInput, TaskerAddTransactionOutput, TaskerAddTransactionRunner>() {
    override val helper by lazy { TaskerAddTransactionHelper(this) }
    override var input = TaskerAddTransactionInput()

    override fun inputFrom(values: Map<String, String>) = TaskerAddTransactionInput.from(values)

    override fun content(
        preferences: Map<String, String>,
        choices: AutomationChoices?,
        variables: List<String>,
    ) = AutomationSetupContent(
        title = "Add transaction",
        intro = "Adds a transaction to the budget open in Tandem. Type a value or a Tasker variable, such as %amount.",
        fields =
            listOf(
                AutomationSetupField("amount", "Amount", "-12.40 for money out, +12.40 for money in", variables),
                AutomationSetupField("payee", "Payee (optional)", "A new name adds a payee", choices?.payees.orEmpty() + variables),
                AutomationSetupField(
                    "category",
                    "Category (optional)",
                    "Unknown names stay uncategorized",
                    choices?.categories.orEmpty() + variables,
                ),
                AutomationSetupField("account", "Account", "Name of an open account", choices?.accounts.orEmpty() + variables),
                AutomationSetupField("date", "Date (optional)", "Today, or year-month-day such as 2026-11-26", variables),
                AutomationSetupField("notes", "Notes (optional)", "", variables),
                AutomationSetupField("cleared", "Cleared (optional)", "true or false", listOf("true", "false")),
                AutomationSetupField("external_id", "Repeat check ID (optional)", "The same ID within 30 days adds nothing", variables),
            ),
        initial = input.values().mapValues { it.value.orEmpty() },
        notice =
            if (!AutomationAccess.writesAllowed(preferences)) {
                "Adding transactions is off. Turn it on in Tandem under Settings → Automation, or this action fails."
            } else {
                lockedNotice(choices)
            },
    )
}

class TaskerReadBudgetActivity : TaskerSettingsActivity<TaskerReadBudgetInput, TaskerReadBudgetOutput, TaskerReadBudgetRunner>() {
    override val helper by lazy { TaskerReadBudgetHelper(this) }
    override var input = TaskerReadBudgetInput()

    override fun inputFrom(values: Map<String, String>) = TaskerReadBudgetInput.from(values)

    override fun content(
        preferences: Map<String, String>,
        choices: AutomationChoices?,
        variables: List<String>,
    ) = AutomationSetupContent(
        title = "Read budget",
        intro =
            "Reads this device’s copy of the open budget without going online. Results: %tandem_left_to_spend, " +
                "%tandem_budgeted, %tandem_spent, %tandem_account_balance and %tandem_to_budget.",
        fields =
            listOf(
                AutomationSetupField(
                    "category",
                    "Category (optional)",
                    "Blank for the whole budget",
                    choices?.categories.orEmpty() + variables,
                ),
                AutomationSetupField(
                    "account",
                    "Account (optional)",
                    "For %tandem_account_balance",
                    choices?.accounts.orEmpty() + variables,
                ),
                AutomationSetupField("month", "Month (optional)", "This month, or year-month such as 2026-11", variables),
            ),
        initial = input.values().mapValues { it.value.orEmpty() },
        notice =
            if (!AutomationAccess.readsAllowed(preferences)) {
                "Reading budget numbers is off. Turn it on in Tandem under Settings → Automation, or this action fails."
            } else {
                lockedNotice(choices)
            },
    )
}
