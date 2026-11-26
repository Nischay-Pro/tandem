package app.tandemforactual.ui.core.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** Today's local ISO date, updated when the day changes so “Today” and “Yesterday” never go stale. */
@Composable
internal fun rememberTodayIsoDate(): String {
    val today by produceState(platformTodayIsoDate()) {
        while (true) {
            delay(TODAY_RECHECK_MILLIS)
            value = platformTodayIsoDate()
        }
    }
    return today
}

private const val TODAY_RECHECK_MILLIS = 30_000L
