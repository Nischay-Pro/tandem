@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.tandemforactual.ui.core.format.amountLabel
import app.tandemforactual.ui.core.format.rememberTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.OccurrenceStatus
import app.tandemforactual.ui.core.state.ScheduledOccurrence
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.formatTransactionListDate
import app.tandemforactual.ui.core.state.liveScheduleSummaries
import app.tandemforactual.ui.core.state.scheduledOccurrences
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowBadge
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.Lucide

private const val SHOWN_OCCURRENCES = 5

/** The account's scheduled transactions for its register, including schedule changes still waiting to sync. */
@Composable
internal fun rememberRegisterOccurrences(
    mobileSession: TandemMobileSession,
    accountId: String?,
): List<ScheduledOccurrence> {
    val today = rememberTodayIsoDate()
    val schedules = mobileSession.scheduleRecords
    val payees = mobileSession.payeeRecords
    val accounts = mobileSession.accountRecords
    val pending = mobileSession.schedules.pendingScheduleActions
    val transactions = mobileSession.transactionRecords
    return remember(accountId, today, schedules, payees, accounts, pending, transactions) {
        if (accountId == null) {
            emptyList()
        } else {
            scheduledOccurrences(liveScheduleSummaries(schedules, payees, accounts, pending), transactions, accountId, today)
        }
    }
}

/** What a scheduled row says about its date: missed, due today, or when it's coming. */
internal fun ScheduledOccurrence.statusLabel(): String {
    val date =
        when (status) {
            OccurrenceStatus.MISSED -> "Missed · ${formatLiveDate(date)}"
            OccurrenceStatus.DUE -> "Due today"
            OccurrenceStatus.UPCOMING -> formatTransactionListDate(date)
        }
    return if (schedule.postsTransaction) "$date · Adds automatically" else date
}

/** An account's missed, due and coming scheduled transactions, each opening its schedule to post or skip it. */
@Composable
internal fun ScheduledOccurrencesCard(
    state: AppState,
    navigation: AppNavigation,
    occurrences: List<ScheduledOccurrence>,
) {
    if (occurrences.isEmpty()) return
    val missed = occurrences.count { it.status == OccurrenceStatus.MISSED }
    SectionCard(
        "Scheduled",
        if (missed > 0) "$missed missed" else "Next ${occurrences.size}",
        action = "See all",
        onClick = { navigation.open(AppRoute.SCHEDULES) },
    ) {
        occurrences.take(SHOWN_OCCURRENCES).forEachIndexed { index, occurrence ->
            if (index > 0) DividerLine()
            ListRow(
                occurrence.schedule.name,
                occurrence.statusLabel(),
                occurrence.schedule.amountLabel(state),
                leading = RowLeading.Icon(Lucide.CalendarClock),
                options =
                    ListRowOptions(
                        trailingColor = tandemPalette.muted,
                        badge = if (occurrence.status == OccurrenceStatus.MISSED) RowBadge(null, tandemPalette.warning, "Missed") else null,
                    ),
                onClick = { navigation.openSchedule(occurrence.schedule.id) },
            )
        }
    }
}
