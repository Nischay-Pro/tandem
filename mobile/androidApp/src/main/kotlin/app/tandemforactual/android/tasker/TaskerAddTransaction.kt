package app.tandemforactual.android.tasker

import android.content.Context
import app.tandemforactual.ui.core.automation.AutomationTransactionInput
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
class TaskerAddTransactionInput
    @JvmOverloads
    constructor(
        @field:TaskerInputField("amount", labelResIdName = "tasker_amount", order = 0) var amount: String? = null,
        @field:TaskerInputField("payee", labelResIdName = "tasker_payee", order = 1) var payee: String? = null,
        @field:TaskerInputField("category", labelResIdName = "tasker_category", order = 2) var category: String? = null,
        @field:TaskerInputField("account", labelResIdName = "tasker_account", order = 3) var account: String? = null,
        @field:TaskerInputField("date", labelResIdName = "tasker_date", order = 4) var date: String? = null,
        @field:TaskerInputField("notes", labelResIdName = "tasker_notes", order = 5) var notes: String? = null,
        @field:TaskerInputField("cleared", labelResIdName = "tasker_cleared", order = 6) var cleared: String? = null,
        @field:TaskerInputField("external_id", labelResIdName = "tasker_external_id", order = 7) var externalId: String? = null,
    ) {
        fun toAutomation() = AutomationTransactionInput(amount, payee, category, account, date, notes, cleared, externalId)

        fun values(): Map<String, String?> =
            mapOf(
                "amount" to amount,
                "payee" to payee,
                "category" to category,
                "account" to account,
                "date" to date,
                "notes" to notes,
                "cleared" to cleared,
                "external_id" to externalId,
            )

        companion object {
            fun from(values: Map<String, String>): TaskerAddTransactionInput {
                fun value(key: String) = values[key]?.takeIf(String::isNotBlank)
                return TaskerAddTransactionInput(
                    value("amount"),
                    value("payee"),
                    value("category"),
                    value("account"),
                    value("date"),
                    value("notes"),
                    value("cleared"),
                    value("external_id"),
                )
            }
        }
    }

@TaskerOutputObject
class TaskerAddTransactionOutput(
    @get:TaskerOutputVariable(
        "tandem_transaction_id",
        labelResIdName = "tasker_output_transaction_id",
        htmlLabelResIdName = "tasker_output_transaction_id_detail",
    )
    val transactionId: String,
)

class TaskerAddTransactionRunner : TaskerPluginRunnerAction<TaskerAddTransactionInput, TaskerAddTransactionOutput>() {
    override fun run(
        context: Context,
        input: TaskerInput<TaskerAddTransactionInput>,
    ): TaskerPluginResult<TaskerAddTransactionOutput> =
        taskerResult {
            val added = runBlocking { AutomationCalls.add(context, input.regular.toAutomation(), TASKER_SOURCE) }
            TaskerAddTransactionOutput(added.transactionId)
        }
}

class TaskerAddTransactionHelper(
    config: TaskerPluginConfig<TaskerAddTransactionInput>,
) : TaskerPluginConfigHelper<TaskerAddTransactionInput, TaskerAddTransactionOutput, TaskerAddTransactionRunner>(config) {
    override val runnerClass = TaskerAddTransactionRunner::class.java
    override val inputClass = TaskerAddTransactionInput::class.java
    override val outputClass = TaskerAddTransactionOutput::class.java
}
