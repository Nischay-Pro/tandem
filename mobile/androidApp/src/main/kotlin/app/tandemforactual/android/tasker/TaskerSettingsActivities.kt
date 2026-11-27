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
            readNotice(preferences, "this action fails", "this action returns an error instead of numbers") ?: lockedNotice(choices),
    )
}

private fun readNotice(
    preferences: Map<String, String>,
    whenOff: String,
    whenHidden: String?,
): String? =
    if (!AutomationAccess.readsAllowed(preferences)) {
        "Reading budget numbers is off. Turn it on in Tandem under Settings → Automation, or $whenOff."
    } else if (whenHidden != null && (preferences["biometricLockEnabled"] == "true" || preferences["privacyModeEnabled"] == "true")) {
        "App lock or Hide budget amounts is on in Tandem, so $whenHidden."
    } else {
        null
    }

private fun categoryField(
    choices: AutomationChoices?,
    variables: List<String>,
) = AutomationSetupField("category", "Category (optional)", "Blank for any category", choices?.categories.orEmpty() + variables)

private const val AFTER_SYNC = "Tandem checks after each background sync, so turn on Schedule background sync in Tandem."

class TaskerOverspentActivity : TaskerSettingsActivity<TaskerCategoryInput, TaskerOverspentOutput, TaskerOverspentRunner>() {
    override val helper by lazy { TaskerOverspentHelper(this) }
    override var input = TaskerCategoryInput()

    override fun inputFrom(values: Map<String, String>) = TaskerCategoryInput(values["category"]?.takeIf(String::isNotBlank))

    override fun content(
        preferences: Map<String, String>,
        choices: AutomationChoices?,
        variables: List<String>,
    ) = AutomationSetupContent(
        title = "Category overspent",
        intro =
            "Fires once when a category goes over budget this month, and again only if it recovers and goes over later. " +
                "Results: %tandem_category and %tandem_overspent_by. $AFTER_SYNC",
        fields = listOf(categoryField(choices, variables)),
        initial = mapOf("category" to input.category.orEmpty()),
        notice = readNotice(preferences, "this event never fires", "this event doesn’t fire") ?: lockedNotice(choices),
    )
}

class TaskerScheduleDueActivity : TaskerSettingsActivity<Unit, TaskerScheduleDueOutput, TaskerScheduleDueRunner>() {
    override val helper by lazy { TaskerScheduleDueHelper(this) }
    override var input = Unit

    override fun inputFrom(values: Map<String, String>) = Unit

    override fun content(
        preferences: Map<String, String>,
        choices: AutomationChoices?,
        variables: List<String>,
    ) = AutomationSetupContent(
        title = "Schedule due today",
        intro =
            "Fires once for each schedule whose next payment is today. Results: %tandem_schedule_name, " +
                "%tandem_amount (negative for money out) and %tandem_due_date. $AFTER_SYNC",
        fields = emptyList(),
        initial = emptyMap(),
        notice = readNotice(preferences, "this event never fires", "this event doesn’t fire"),
    )
}

class TaskerSyncFailedActivity : TaskerSettingsActivity<Unit, TaskerSyncFailedOutput, TaskerSyncFailedRunner>() {
    override val helper by lazy { TaskerSyncFailedHelper(this) }
    override var input = Unit

    override fun inputFrom(values: Map<String, String>) = Unit

    override fun content(
        preferences: Map<String, String>,
        choices: AutomationChoices?,
        variables: List<String>,
    ) = AutomationSetupContent(
        title = "Sync failed",
        intro =
            "Fires when a background sync fails, once until a sync works again. Result: %tandem_error, " +
                "which says what to do next. It names no amounts, so it fires even while app lock or Hide budget amounts is on.",
        fields = emptyList(),
        initial = emptyMap(),
        notice = readNotice(preferences, "this event never fires", null),
    )
}

class TaskerOverBudgetActivity : TaskerSettingsActivity<TaskerCategoryInput, TaskerOverspentOutput, TaskerOverBudgetRunner>() {
    override val helper by lazy { TaskerOverBudgetHelper(this) }
    override var input = TaskerCategoryInput()

    override fun inputFrom(values: Map<String, String>) = TaskerCategoryInput(values["category"]?.takeIf(String::isNotBlank))

    override fun content(
        preferences: Map<String, String>,
        choices: AutomationChoices?,
        variables: List<String>,
    ) = AutomationSetupContent(
        title = "Category over budget",
        intro =
            "True while the category, or any category when blank, is over budget this month on this device. " +
                "Results: %tandem_category (names, comma-separated) and %tandem_overspent_by (in total).",
        fields = listOf(categoryField(choices, variables)),
        initial = mapOf("category" to input.category.orEmpty()),
        notice = readNotice(preferences, "this state is never true", "this state is never true") ?: lockedNotice(choices),
    )
}
