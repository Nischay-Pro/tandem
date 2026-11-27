@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.tandemforactual.data.store.watchSummaryJson
import app.tandemforactual.ui.core.format.formatBudgetCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.platform.LocalWatchLink
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val WATCH_SUMMARY_SETTLE_MS = 600L

/**
 * Sends the paired watch a fresh summary while Tandem is open: after each sync, edit, budget switch or settings change.
 * Only here, with the budget unlocked on screen, can a protected budget's numbers reach the watch.
 */
@Composable
internal fun PublishWatchSummary(
    ctx: ShellContext,
    isAppInForeground: Boolean,
) {
    val link = LocalWatchLink.current ?: return
    val paired by link.paired.collectAsState()
    if (!paired) return
    val state = ctx.state
    val session = ctx.mobileSession
    val encryptedOpen =
        !state.selectedBudgetEncrypted ||
            (state.encryptedPassword.isNotBlank() && session.boundEncryptedBudgetId == state.selectedBudgetId)
    val opened = isAppInForeground && (!state.biometricLockEnabled || ctx.shell.appLockState.unlocked) && encryptedOpen
    LaunchedEffect(
        link,
        opened,
        state.selectedBudgetId,
        state.privacyModeEnabled,
        state.readOnlyMode,
        state.biometricLockEnabled,
        state.watchSettings,
        state.lastTransactionAccountId,
        session.lastSyncSummary,
        session.pendingWrites.size,
        session.databaseGeneration,
    ) {
        delay(WATCH_SUMMARY_SETTLE_MS)
        val repository = session.repository ?: return@LaunchedEffect
        val preferences = state.persistentSettings()
        val json =
            withContext(Dispatchers.Default) {
                runCatching {
                    watchSummaryJson(
                        repository,
                        preferences,
                        platformTodayIsoDate(),
                        Clock.System.now().toEpochMilliseconds(),
                        opened,
                        ::formatBudgetCurrency,
                    )
                }.getOrNull()
            }
        json?.let(link::sendSummary)
    }
}
