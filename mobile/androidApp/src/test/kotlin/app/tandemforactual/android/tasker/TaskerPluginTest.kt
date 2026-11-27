package app.tandemforactual.android.tasker

import app.tandemforactual.ui.core.automation.AutomationErrorCode
import app.tandemforactual.ui.core.automation.AutomationException
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskerPluginTest {
    @Test
    fun refusalsReachTaskerAsErrors() {
        val refused = taskerResult<String> { throw AutomationException(AutomationErrorCode.WRITES_OFF, "Adding transactions is off.") }
        assertEquals("Adding transactions is off.", (refused as TaskerPluginResultErrorWithOutput).message)
        val failed = taskerResult<String> { error("disk full") }
        assertTrue((failed as TaskerPluginResultErrorWithOutput).message.contains("Open Tandem and try again"))
        assertEquals("id", (taskerResult { "id" } as TaskerPluginResultSucess).regular)
    }

    @Test
    fun settingsRoundTripThroughTheirFields() {
        val input =
            TaskerAddTransactionInput.from(
                mapOf("amount" to "%amount", "payee" to "Café", "notes" to "", "external_id" to "%sms_id"),
            )
        assertEquals("%amount", input.amount)
        assertEquals(null, input.notes)
        assertEquals("%sms_id", input.toAutomation().externalId)
        assertEquals("Café", input.values()["payee"])
        assertEquals("2026-11", TaskerReadBudgetInput.from(mapOf("month" to "2026-11")).toAutomation().month)
    }
}
