package app.tandemforactual.android.assistant

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDeniedException
import androidx.appfunctions.AppFunctionDisabledException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import app.tandemforactual.ui.core.automation.AutomationErrorCode
import app.tandemforactual.ui.core.automation.AutomationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs off the main thread; a refusal reaches the assistant as the matching AppFunctions error with Tandem's message. */
internal suspend fun <T> assistantCall(block: suspend () -> T): T =
    try {
        withContext(Dispatchers.IO) { block() }
    } catch (error: CancellationException) {
        throw error
    } catch (error: AutomationException) {
        throw error.toAppFunctionException()
    } catch (_: Exception) {
        throw AppFunctionAppUnknownException("Tandem couldn’t finish this. Open Tandem and try again.")
    }

internal fun AutomationException.toAppFunctionException(): AppFunctionException {
    val text = message.orEmpty()
    return when (code) {
        AutomationErrorCode.WRITES_OFF, AutomationErrorCode.READS_OFF -> AppFunctionDisabledException(text)

        AutomationErrorCode.READ_ONLY,
        AutomationErrorCode.BUDGET_LOCKED,
        AutomationErrorCode.APP_LOCKED,
        AutomationErrorCode.AMOUNTS_HIDDEN,
        -> AppFunctionDeniedException(text)

        AutomationErrorCode.NO_BUDGET,
        AutomationErrorCode.CATEGORY,
        AutomationErrorCode.NOT_ON_DEVICE,
        -> AppFunctionElementNotFoundException(text)

        AutomationErrorCode.SAVE_FAILED -> AppFunctionAppUnknownException(text)

        else -> AppFunctionInvalidArgumentException(text)
    }
}
