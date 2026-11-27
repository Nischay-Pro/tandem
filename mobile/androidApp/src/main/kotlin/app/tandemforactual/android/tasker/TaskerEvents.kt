package app.tandemforactual.android.tasker

import android.content.Context
import app.tandemforactual.ui.core.automation.AutomationEvent
import app.tandemforactual.ui.core.automation.AutomationEventKind
import app.tandemforactual.ui.core.automation.AutomationEventValue
import app.tandemforactual.ui.core.automation.matchesCategory
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionState
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import kotlinx.coroutines.runBlocking

@TaskerInputRoot
class TaskerCategoryInput
    @JvmOverloads
    constructor(
        @field:TaskerInputField("category", labelResIdName = "tasker_category", order = 0) var category: String? = null,
    )

/** All the wake-up broadcast carries: the event's id. Its details are read back from Tandem's database. */
@TaskerInputRoot
class TaskerEventUpdate
    @JvmOverloads
    constructor(
        @field:TaskerInputField("id") var id: String? = null,
    )

@TaskerOutputObject
class TaskerOverspentOutput(
    @get:TaskerOutputVariable(
        "tandem_category",
        labelResIdName = "tasker_category",
        htmlLabelResIdName = "tasker_output_category_detail",
    )
    val category: String,
    @get:TaskerOutputVariable(
        "tandem_overspent_by",
        labelResIdName = "tasker_output_overspent",
        htmlLabelResIdName = "tasker_output_overspent_detail",
    )
    val overspentBy: String,
)

@TaskerOutputObject
class TaskerScheduleDueOutput(
    @get:TaskerOutputVariable(
        "tandem_schedule_name",
        labelResIdName = "tasker_output_schedule",
        htmlLabelResIdName = "tasker_output_schedule_detail",
    )
    val schedule: String,
    @get:TaskerOutputVariable("tandem_amount", labelResIdName = "tasker_amount", htmlLabelResIdName = "tasker_output_amount_detail")
    val amount: String,
    @get:TaskerOutputVariable("tandem_due_date", labelResIdName = "tasker_date", htmlLabelResIdName = "tasker_output_due_detail")
    val dueDate: String,
)

@TaskerOutputObject
class TaskerSyncFailedOutput(
    @get:TaskerOutputVariable("tandem_error", labelResIdName = "tasker_output_error", htmlLabelResIdName = "tasker_output_error_detail")
    val error: String,
)

private fun <O> satisfied(
    context: Context,
    output: O?,
): TaskerPluginResultCondition<O> =
    if (output == null) TaskerPluginResultConditionUnsatisfied() else TaskerPluginResultConditionSatisfied(context, output)

private fun overspentOutput(values: Map<String, String>) =
    TaskerOverspentOutput(values[AutomationEventValue.CATEGORY].orEmpty(), values[AutomationEventValue.OVERSPENT_BY].orEmpty())

private fun storedEvent(
    context: Context,
    update: TaskerEventUpdate?,
    kind: AutomationEventKind,
): AutomationEvent? = update?.id?.let { id -> runBlocking { AutomationCalls.event(context, id, kind) } }

class TaskerOverspentRunner : TaskerPluginRunnerConditionEvent<TaskerCategoryInput, TaskerOverspentOutput, TaskerEventUpdate>() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<TaskerCategoryInput>,
        update: TaskerEventUpdate?,
    ): TaskerPluginResultCondition<TaskerOverspentOutput> {
        val event =
            storedEvent(context, update, AutomationEventKind.CATEGORY_OVERSPENT)
                ?.takeIf { it.matchesCategory(input.regular.category) }
        return satisfied(context, event?.values?.let(::overspentOutput))
    }
}

class TaskerScheduleDueRunner : TaskerPluginRunnerConditionEvent<Unit, TaskerScheduleDueOutput, TaskerEventUpdate>() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<Unit>,
        update: TaskerEventUpdate?,
    ): TaskerPluginResultCondition<TaskerScheduleDueOutput> {
        val event = storedEvent(context, update, AutomationEventKind.SCHEDULE_DUE)
        return satisfied(
            context,
            event?.let {
                TaskerScheduleDueOutput(
                    it.value(AutomationEventValue.SCHEDULE),
                    it.value(AutomationEventValue.AMOUNT),
                    it.value(AutomationEventValue.DUE_DATE),
                )
            },
        )
    }
}

class TaskerSyncFailedRunner : TaskerPluginRunnerConditionEvent<Unit, TaskerSyncFailedOutput, TaskerEventUpdate>() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<Unit>,
        update: TaskerEventUpdate?,
    ): TaskerPluginResultCondition<TaskerSyncFailedOutput> =
        satisfied(
            context,
            storedEvent(context, update, AutomationEventKind.SYNC_FAILED)?.let {
                TaskerSyncFailedOutput(it.value(AutomationEventValue.ERROR))
            },
        )
}

/** True while a category, or any category, is over budget this month on this device; never true while reads are closed. */
class TaskerOverBudgetRunner : TaskerPluginRunnerConditionState<TaskerCategoryInput, TaskerOverspentOutput>() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<TaskerCategoryInput>,
        update: Unit?,
    ): TaskerPluginResultCondition<TaskerOverspentOutput> {
        val values = runBlocking { AutomationCalls.overBudget(context, input.regular.category) }
        return satisfied(context, values?.let(::overspentOutput))
    }
}

private fun AutomationEvent.value(key: String): String = values[key].orEmpty()

class TaskerOverspentHelper(
    config: TaskerPluginConfig<TaskerCategoryInput>,
) : TaskerPluginConfigHelper<TaskerCategoryInput, TaskerOverspentOutput, TaskerOverspentRunner>(config) {
    override val runnerClass = TaskerOverspentRunner::class.java
    override val inputClass = TaskerCategoryInput::class.java
    override val outputClass = TaskerOverspentOutput::class.java
}

class TaskerScheduleDueHelper(
    config: TaskerPluginConfig<Unit>,
) : TaskerPluginConfigHelper<Unit, TaskerScheduleDueOutput, TaskerScheduleDueRunner>(config) {
    override val runnerClass = TaskerScheduleDueRunner::class.java
    override val inputClass = Unit::class.java
    override val outputClass = TaskerScheduleDueOutput::class.java
}

class TaskerSyncFailedHelper(
    config: TaskerPluginConfig<Unit>,
) : TaskerPluginConfigHelper<Unit, TaskerSyncFailedOutput, TaskerSyncFailedRunner>(config) {
    override val runnerClass = TaskerSyncFailedRunner::class.java
    override val inputClass = Unit::class.java
    override val outputClass = TaskerSyncFailedOutput::class.java
}

class TaskerOverBudgetHelper(
    config: TaskerPluginConfig<TaskerCategoryInput>,
) : TaskerPluginConfigHelper<TaskerCategoryInput, TaskerOverspentOutput, TaskerOverBudgetRunner>(config) {
    override val runnerClass = TaskerOverBudgetRunner::class.java
    override val inputClass = TaskerCategoryInput::class.java
    override val outputClass = TaskerOverspentOutput::class.java
}

/** Wakes Tasker for one event; the broadcast names the event's id only, never its names or amounts. */
internal fun sendToTasker(
    context: Context,
    event: AutomationEvent,
) {
    val activity =
        when (event.kind) {
            AutomationEventKind.CATEGORY_OVERSPENT -> TaskerOverspentActivity::class.java
            AutomationEventKind.SCHEDULE_DUE -> TaskerScheduleDueActivity::class.java
            AutomationEventKind.SYNC_FAILED -> TaskerSyncFailedActivity::class.java
        }
    runCatching { activity.requestQuery(context, TaskerEventUpdate(event.id)) }
}

/** Asks Tasker to check "Category over budget" again now that the numbers may have changed. */
internal fun recheckTaskerStates(context: Context) {
    runCatching { TaskerOverBudgetActivity::class.java.requestQuery(context) }
}
