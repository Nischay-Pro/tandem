@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.auth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState

internal const val APP_LOCK_GRACE_MILLIS = 60_000L

internal const val UNLOCK_ATTEMPTS_BEFORE_WAIT = 5

private val UNLOCK_WAIT_STEPS_MILLIS = listOf(30_000L, 120_000L, 600_000L, 1_800_000L)

internal fun unlockWaitMillis(failures: Int): Long {
    if (failures <= 0 || failures % UNLOCK_ATTEMPTS_BEFORE_WAIT != 0) return 0L
    val step = failures / UNLOCK_ATTEMPTS_BEFORE_WAIT - 1
    return UNLOCK_WAIT_STEPS_MILLIS[step.coerceAtMost(UNLOCK_WAIT_STEPS_MILLIS.lastIndex)]
}

internal class AppLockState(
    private val now: () -> Long,
) {
    var unlocked by mutableStateOf(false)
        private set
    private var backgroundedAt by mutableLongStateOf(-1L)

    fun onForegroundChanged(foreground: Boolean) {
        if (!foreground) {
            backgroundedAt = now()
        } else if (backgroundedAt >= 0 && now() - backgroundedAt >= APP_LOCK_GRACE_MILLIS) {
            unlocked = false
            backgroundedAt = -1L
        }
    }

    fun unlock() {
        unlocked = true
        backgroundedAt = -1L
    }
}

internal fun encryptedBudgetUnlockAccepted(
    syncSucceeded: Boolean,
    serverReachable: Boolean,
    offlineCheck: Boolean?,
): Boolean = syncSucceeded || (!serverReachable && offlineCheck == true)

internal fun needsEncryptedBudgetUnlock(
    state: AppState,
    route: AppRoute,
): Boolean =
    state.selectedBudgetEncrypted &&
        state.selectedBudgetId.isNotBlank() &&
        state.encryptedPassword.isBlank() &&
        route !in ENCRYPTED_UNLOCK_EXEMPT_ROUTES

private val ENCRYPTED_UNLOCK_EXEMPT_ROUTES =
    setOf(
        AppRoute.WELCOME,
        AppRoute.SERVER_ADDRESS,
        AppRoute.SIGN_IN,
        AppRoute.BUDGET_CHOOSER,
        AppRoute.SWITCH_BUDGET,
        AppRoute.BUDGET_UNLOCK,
        AppRoute.FIRST_SYNC,
    )
