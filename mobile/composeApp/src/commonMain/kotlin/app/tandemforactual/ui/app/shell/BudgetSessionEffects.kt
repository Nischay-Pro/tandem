@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.tandemforactual.ui.core.security.budgetPasswordSecretId
import app.tandemforactual.ui.core.session.forgetEncryptionPasswords
import app.tandemforactual.ui.core.session.loadBudgets
import app.tandemforactual.ui.core.session.observeLocalRecords
import app.tandemforactual.ui.core.session.observeSelectedBudgetMonth
import app.tandemforactual.ui.core.session.unbindEncryptedBudgetDatabase
import app.tandemforactual.ui.core.session.unlockSecret
import app.tandemforactual.ui.core.state.AppRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads the budget list, runs the first sync, and keeps the session bound to the selected budget and month. */
@Composable
internal fun BudgetSessionEffects(ctx: ShellContext) {
    val state = ctx.state
    val mobileSession = ctx.mobileSession
    val route = ctx.route
    LaunchedEffect(route, mobileSession.authenticatedDevice?.deviceId) {
        if (
            route == AppRoute.BUDGET_CHOOSER &&
            mobileSession.isAuthenticated &&
            !mobileSession.budgetCatalogLoaded &&
            mobileSession.budgets.isEmpty()
        ) {
            mobileSession.loadBudgets()
        }
    }
    LaunchedEffect(route) {
        mobileSession.feedback.clearBudgetActionMessage()
    }
    FirstSyncEffect(ctx)
    LaunchedEffect(state.selectedBudgetEncrypted, state.selectedBudgetId, ctx.env.encryptedBudgetVault) {
        mobileSession.encryptedStorageRequired =
            if (ctx.env.encryptedBudgetVault != null && state.selectedBudgetEncrypted && state.selectedBudgetId.isNotBlank()) {
                setOf(state.selectedBudgetId)
            } else {
                emptySet()
            }
    }
    LaunchedEffect(state.encryptedPassword.isBlank(), state.selectedBudgetId) {
        if (state.encryptedPassword.isBlank()) mobileSession.forgetEncryptionPasswords()
        val bound = mobileSession.boundEncryptedBudgetId
        if (bound != null && (state.encryptedPassword.isBlank() || bound != state.selectedBudgetId)) {
            mobileSession.unbindEncryptedBudgetDatabase()
        }
    }
    LaunchedEffect(
        state.selectedBudgetId,
        mobileSession,
        mobileSession.databaseGeneration,
        mobileSession.authenticatedDevice?.principalId,
    ) {
        if (state.selectedBudgetId.isNotBlank()) {
            mobileSession.observeLocalRecords(state.selectedBudgetId)
        }
    }
    LaunchedEffect(
        state.selectedBudgetId,
        state.selectedMonthId,
        mobileSession,
        mobileSession.databaseGeneration,
        mobileSession.authenticatedDevice?.principalId,
    ) {
        mobileSession.observeSelectedBudgetMonth(state.selectedBudgetId, state.selectedMonthId)
    }
}

@Composable
private fun FirstSyncEffect(ctx: ShellContext) {
    val state = ctx.state
    val mobileSession = ctx.mobileSession
    val deviceAuthenticator = ctx.env.deviceAuthenticator
    LaunchedEffect(ctx.route, state.selectedBudgetId, ctx.shell.syncRetryCounter) {
        if (ctx.route == AppRoute.FIRST_SYNC && state.selectedBudgetId.isNotBlank()) {
            mobileSession.synchronize(
                budgetId = state.selectedBudgetId,
                encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
            )
            val unlockedPassword = state.encryptedPassword
            if (mobileSession.syncComplete && state.selectedBudgetEncrypted && unlockedPassword.isNotBlank()) {
                val secretId = budgetPasswordSecretId(state.selectedBudgetId)
                withContext(Dispatchers.Default) { deviceAuthenticator?.rememberPasswordCheck(secretId, unlockedPassword) }
                if (state.rememberBudgetPasswordWithBiometrics && deviceAuthenticator?.canStoreSecrets() == true) {
                    deviceAuthenticator.storeSecret(
                        secretId,
                        unlockSecret(ctx.env.encryptedBudgetVault, state.selectedBudgetId, unlockedPassword),
                        "Use biometrics for ${state.selectedBudget}",
                    ) {}
                } else {
                    deviceAuthenticator?.deleteSecret(secretId)
                }
            }
        }
    }
}
