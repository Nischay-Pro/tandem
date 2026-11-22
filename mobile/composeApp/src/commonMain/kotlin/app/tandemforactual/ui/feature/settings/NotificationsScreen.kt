@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.data.store.QuietHours
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.CompactActionButton
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

@Composable
internal fun NotificationsScreen(
    state: AppState,
    onRequestNotificationPermission: (() -> Unit)?,
    onOpenNotificationSettings: (() -> Unit)?,
    notificationPermissionEnabled: Boolean?,
    onToggleNotificationPermission: ((Boolean) -> Unit)?,
) {
    PageColumn {
        NotificationPermissionCard(
            onRequestNotificationPermission,
            onOpenNotificationSettings,
            notificationPermissionEnabled,
            onToggleNotificationPermission,
        )
        val askOnEnable: (Boolean) -> Unit = { on ->
            if (on && notificationPermissionEnabled == false) onRequestNotificationPermission?.invoke()
        }
        SectionCard("Sync and safety") {
            ToggleRow("Sync reminders", "When background sync fails", state.syncReminders) { state.syncReminders = it }
            DividerLine()
            ToggleRow("Needs review alerts", "When a change needs review", state.conflictReviewAlerts) {
                state.conflictReviewAlerts = it
            }
            DividerLine()
            ToggleRow("Rejected edit alerts", "When Actual rejects an edit", state.rejectedEditAlerts) {
                state.rejectedEditAlerts = it
            }
            DividerLine()
            ToggleRow("Bank sync failures", "When a linked account can’t sync with its bank", state.bankSyncAlerts) {
                state.bankSyncAlerts = it
            }
        }
        MoneyAlertsSection(state, askOnEnable)
        QuietHoursSection(state)
        if (state.privacyModeEnabled) InfoBubble("Hide amounts is on, so notifications leave amounts out.")
        InfoBubble("In-app banners always show, even when phone notifications are off.")
    }
}

/** The system notification permission, as a switch or as buttons to turn it on. */
@Composable
private fun NotificationPermissionCard(
    onRequestNotificationPermission: (() -> Unit)?,
    onOpenNotificationSettings: (() -> Unit)?,
    notificationPermissionEnabled: Boolean?,
    onToggleNotificationPermission: ((Boolean) -> Unit)?,
) {
    SurfaceCard {
        if (notificationPermissionEnabled != null && onToggleNotificationPermission != null) {
            ToggleRow(
                "Allow notifications",
                if (notificationPermissionEnabled) {
                    "On · Turn off in notification settings"
                } else {
                    "Off · Turn on in notification settings"
                },
                notificationPermissionEnabled,
                onChange = onToggleNotificationPermission,
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                Text(
                    "Notifications",
                    modifier = Modifier.weight(1f),
                    color = tandemPalette.text,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.Medium,
                )
                if (onRequestNotificationPermission != null) {
                    CompactActionButton("Turn on", Modifier.weight(1f), onRequestNotificationPermission)
                }
                if (onOpenNotificationSettings != null) {
                    CompactActionButton("Settings", Modifier.weight(1f), onOpenNotificationSettings)
                }
            }
        }
    }
}

/** Bill, overspending and large-transaction alerts. */
@Composable
private fun MoneyAlertsSection(
    state: AppState,
    askOnEnable: (Boolean) -> Unit,
) {
    SectionCard("Money") {
        ToggleRow(
            "Upcoming bills",
            "From your schedules, ${billLeadLabel(state.billLeadDays)}",
            state.upcomingBillAlerts,
        ) {
            state.upcomingBillAlerts = it
            askOnEnable(it)
        }
        RevealedOption(state.upcomingBillAlerts) {
            FilterPills(listOf(1, 3, 7).map { it to billLeadLabel(it) }, state.billLeadDays, { state.billLeadDays = it })
        }
        DividerLine()
        ToggleRow("Overspending alerts", "When a category goes over budget this month", state.overspendingAlerts) {
            state.overspendingAlerts = it
        }
        DividerLine()
        ToggleRow(
            "Large transactions",
            "Spending of ${state.formatCurrency(state.largeTransactionMinor)} or more",
            state.largeTransactionAlerts,
        ) {
            state.largeTransactionAlerts = it
            askOnEnable(it)
        }
        RevealedOption(state.largeTransactionAlerts) {
            FilterPills(
                LARGE_TRANSACTION_STEPS.map { it to state.formatCurrency(it) },
                state.largeTransactionMinor,
                { state.largeTransactionMinor = it },
            )
        }
    }
}

/** Whether and when notifications stay silent. */
@Composable
private fun QuietHoursSection(state: AppState) {
    val quiet = QuietHours.parse(state.quietHours)
    SectionCard("Quiet hours") {
        ToggleRow(
            "Quiet hours",
            quiet?.let { "Silent ${quietHoursLabel(it)}" } ?: "Notifications can arrive at any time",
            quiet != null,
        ) { state.quietHours = if (it) QUIET_HOURS_PRESETS.first().encode() else "" }
        RevealedOption(quiet != null) {
            FilterPills(QUIET_HOURS_PRESETS.map { it to quietHoursLabel(it) }, quiet ?: QUIET_HOURS_PRESETS.first(), {
                state.quietHours = it.encode()
            })
        }
    }
}

private val LARGE_TRANSACTION_STEPS = listOf(10_000L, 25_000L, 50_000L, 100_000L)

private val QUIET_HOURS_PRESETS = listOf(QuietHours(22 * 60, 7 * 60), QuietHours(23 * 60, 6 * 60), QuietHours(21 * 60, 8 * 60))

private fun billLeadLabel(days: Int): String = if (days == 1) "1 day ahead" else "$days days ahead"

@Composable
private fun RevealedOption(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val reduceMotion = tandemReduceMotion
    AnimatedVisibility(
        visible,
        enter =
            fadeIn(tandemMotionSpec(reduceMotion, TandemMotion.CONTROL_MILLIS)) +
                expandVertically(tandemMotionSpec(reduceMotion, TandemMotion.CONTENT_SIZE_MILLIS)),
        exit =
            fadeOut(tandemMotionSpec(reduceMotion, TandemMotion.CONTROL_MILLIS)) +
                shrinkVertically(tandemMotionSpec(reduceMotion, TandemMotion.CONTENT_SIZE_MILLIS)),
    ) {
        Box(Modifier.fillMaxWidth().padding(start = TandemSpacing.large, end = TandemSpacing.large, bottom = TandemSpacing.medium)) {
            content()
        }
    }
}
