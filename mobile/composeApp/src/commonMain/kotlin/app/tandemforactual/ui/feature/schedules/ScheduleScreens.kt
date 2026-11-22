@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.schedules
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal fun scheduleFieldErrorMessage(
    message: String?,
    messageField: String?,
    field: String,
): String? = message?.takeIf { messageField == field }
