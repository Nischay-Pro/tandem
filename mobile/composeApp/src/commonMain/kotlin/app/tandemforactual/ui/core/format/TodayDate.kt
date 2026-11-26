@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

private val LocalTodayIsoDate = compositionLocalOf { platformTodayIsoDate() }

/** Today's local ISO date, so “Today” and “Yesterday” never go stale. */
@Composable
internal fun rememberTodayIsoDate(): String = LocalTodayIsoDate.current

/** Provides today's date to the app: re-read when the app comes forward and at each local midnight, never polled. */
@Composable
internal fun ProvideTodayIsoDate(
    appInForeground: Boolean,
    content: @Composable () -> Unit,
) {
    val today by produceState(platformTodayIsoDate(), appInForeground) {
        if (!appInForeground) return@produceState
        while (true) {
            value = platformTodayIsoDate()
            delay(millisUntilNextDay(platformMinuteOfDay()))
        }
    }
    CompositionLocalProvider(LocalTodayIsoDate provides today, content = content)
}

internal fun millisUntilNextDay(minuteOfDay: Int): Long = (MINUTES_PER_DAY - minuteOfDay).coerceAtLeast(1) * MILLIS_PER_MINUTE

private const val MINUTES_PER_DAY = 24 * 60
private const val MILLIS_PER_MINUTE = 60_000L
