@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.store.QuietHours
import app.tandemforactual.ui.core.format.platformFormatTimeOfDay

internal fun quietHoursLabel(hours: QuietHours): String =
    "${platformFormatTimeOfDay(hours.startMinute)} – ${platformFormatTimeOfDay(hours.endMinute)}"
