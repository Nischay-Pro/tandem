package app.tandemforactual.android.tasker

import app.tandemforactual.ui.core.automation.AutomationException
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess

internal const val TASKER_SOURCE = "Tasker"
private const val UNEXPECTED_ERROR = 99

/** Runs a plugin action; a refusal or failure reaches Tasker as %err and %errmsg instead of crashing the task. */
internal fun <T> taskerResult(block: () -> T): TaskerPluginResult<T> =
    try {
        TaskerPluginResultSucess(block())
    } catch (error: AutomationException) {
        TaskerPluginResultErrorWithOutput(error.code, error.message.orEmpty())
    } catch (_: Exception) {
        TaskerPluginResultErrorWithOutput(UNEXPECTED_ERROR, "Tandem couldn’t finish this action. Open Tandem and try again.")
    }
