package app.tandemforactual.android.tasker

import android.content.Context
import app.tandemforactual.ui.core.automation.AutomationReadInput
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import kotlinx.coroutines.runBlocking

@TaskerInputRoot
class TaskerReadBudgetInput
    @JvmOverloads
    constructor(
        @field:TaskerInputField("category", labelResIdName = "tasker_category", order = 0) var category: String? = null,
        @field:TaskerInputField("account", labelResIdName = "tasker_account", order = 1) var account: String? = null,
        @field:TaskerInputField("month", labelResIdName = "tasker_month", order = 2) var month: String? = null,
    ) {
        fun toAutomation() = AutomationReadInput(category, account, month)

        fun values(): Map<String, String?> = mapOf("category" to category, "account" to account, "month" to month)

        companion object {
            fun from(values: Map<String, String>): TaskerReadBudgetInput {
                fun value(key: String) = values[key]?.takeIf(String::isNotBlank)
                return TaskerReadBudgetInput(value("category"), value("account"), value("month"))
            }
        }
    }

@TaskerOutputObject
class TaskerReadBudgetOutput(
    @get:TaskerOutputVariable(
        "tandem_left_to_spend",
        labelResIdName = "tasker_output_left",
        htmlLabelResIdName = "tasker_output_left_detail",
    )
    val leftToSpend: String,
    @get:TaskerOutputVariable(
        "tandem_budgeted",
        labelResIdName = "tasker_output_budgeted",
        htmlLabelResIdName = "tasker_output_budgeted_detail",
    )
    val budgeted: String,
    @get:TaskerOutputVariable("tandem_spent", labelResIdName = "tasker_output_spent", htmlLabelResIdName = "tasker_output_spent_detail")
    val spent: String,
    @get:TaskerOutputVariable(
        "tandem_account_balance",
        labelResIdName = "tasker_output_balance",
        htmlLabelResIdName = "tasker_output_balance_detail",
    )
    val accountBalance: String,
    @get:TaskerOutputVariable(
        "tandem_to_budget",
        labelResIdName = "tasker_output_to_budget",
        htmlLabelResIdName = "tasker_output_to_budget_detail",
    )
    val toBudget: String,
)

class TaskerReadBudgetRunner : TaskerPluginRunnerAction<TaskerReadBudgetInput, TaskerReadBudgetOutput>() {
    override fun run(
        context: Context,
        input: TaskerInput<TaskerReadBudgetInput>,
    ): TaskerPluginResult<TaskerReadBudgetOutput> =
        taskerResult {
            val numbers = runBlocking { AutomationCalls.read(context, input.regular.toAutomation()) }
            TaskerReadBudgetOutput(
                leftToSpend = numbers.text(numbers.leftToSpendMinor),
                budgeted = numbers.text(numbers.budgetedMinor),
                spent = numbers.text(numbers.spentMinor),
                accountBalance = numbers.text(numbers.accountBalanceMinor),
                toBudget = numbers.text(numbers.toBudgetMinor),
            )
        }
}

class TaskerReadBudgetHelper(
    config: TaskerPluginConfig<TaskerReadBudgetInput>,
) : TaskerPluginConfigHelper<TaskerReadBudgetInput, TaskerReadBudgetOutput, TaskerReadBudgetRunner>(config) {
    override val runnerClass = TaskerReadBudgetRunner::class.java
    override val inputClass = TaskerReadBudgetInput::class.java
    override val outputClass = TaskerReadBudgetOutput::class.java
}
