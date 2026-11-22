package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** How long reorders wait on the device before they are sent together, unless Sync now is tapped. */
internal const val BUDGET_ORDER_FLUSH_INTERVAL_MS = 60_000L

internal const val BUDGET_QUEUE_RESTORE_ERROR = "Couldn’t load saved category changes. Try again before editing categories."

internal const val BUDGET_CACHE_OWNER_KEY = "internal:budget-cache-owner-v1"

internal fun budgetCacheOwner(
    serverUrl: String,
    principalId: String,
): String =
    buildJsonObject {
        put("serverUrl", serverUrl.trim().trimEnd('/'))
        put("principalId", principalId)
    }.toString()

internal const val BUDGET_PASSWORD_ATTEMPTS_EXCEEDED = "budget_password_attempts_exceeded"

internal val BUDGET_PASSWORD_ERROR_CODES =
    setOf("budget_password_required", "budget_password_incorrect", BUDGET_PASSWORD_ATTEMPTS_EXCEEDED)

internal const val ENCRYPTED_DATABASE_CLOSE_DELAY_MILLIS = 3_000L

internal const val RECENT_PAYEE_COUNT = 5
