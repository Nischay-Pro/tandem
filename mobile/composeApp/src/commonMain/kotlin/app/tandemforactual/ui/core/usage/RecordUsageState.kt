package app.tandemforactual.ui.core.usage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RecordUsage
import app.tandemforactual.data.store.accountUsage
import app.tandemforactual.data.store.categoryGroupUsage
import app.tandemforactual.data.store.categoryUsage
import app.tandemforactual.data.store.payeeUsage
import app.tandemforactual.data.store.ruleUsage
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import kotlinx.coroutines.CancellationException

internal enum class UsageSubject {
    PAYEE,
    CATEGORY,
    CATEGORY_GROUP,
    ACCOUNT,
    RULE,
}

internal sealed interface UsageState {
    data object Checking : UsageState

    data class Ready(
        val usage: RecordUsage,
    ) : UsageState

    data object Unavailable : UsageState
}

internal val UsageState.isChecking: Boolean get() = this == UsageState.Checking

/** Counts what [recordId] is tied to in the local copy of the budget, starting over when the record changes. */
@Composable
internal fun rememberRecordUsage(
    mobileSession: TandemMobileSession,
    budgetId: String,
    subject: UsageSubject,
    recordId: String,
): UsageState {
    val usage by produceState<UsageState>(UsageState.Checking, budgetId, subject, recordId) {
        value = UsageState.Checking
        value = loadRecordUsage(mobileSession.repository, budgetId, subject, recordId)
    }
    return usage
}

internal suspend fun loadRecordUsage(
    repository: LocalBudgetRepository?,
    budgetId: String,
    subject: UsageSubject,
    recordId: String,
): UsageState {
    if (repository == null || budgetId.isBlank() || recordId.isBlank()) return UsageState.Unavailable
    return try {
        UsageState.Ready(
            when (subject) {
                UsageSubject.PAYEE -> repository.payeeUsage(budgetId, recordId)
                UsageSubject.CATEGORY -> repository.categoryUsage(budgetId, recordId)
                UsageSubject.CATEGORY_GROUP -> repository.categoryGroupUsage(budgetId, recordId)
                UsageSubject.ACCOUNT -> repository.accountUsage(budgetId, recordId, platformTodayIsoDate())
                UsageSubject.RULE -> repository.ruleUsage(budgetId, recordId)
            },
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        UsageState.Unavailable
    }
}
