package app.tandemforactual.ui.core.automation

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.ui.core.session.BackgroundBudgetKey

/** What Settings → Automation lets other apps do; both stay off until the user turns them on. */
object AutomationAccess {
    const val WRITES = "automationWritesEnabled"
    const val READS = "automationReadsEnabled"

    fun writesAllowed(preferences: Map<String, String>): Boolean = preferences[WRITES] == "true"

    fun readsAllowed(preferences: Map<String, String>): Boolean = preferences[READS] == "true"
}

/** A failed automation call; [code] and the message reach the caller, such as Tasker's %err and %errmsg. */
class AutomationException(
    val code: Int,
    message: String,
) : Exception(message)

object AutomationErrorCode {
    const val WRITES_OFF = 1
    const val READS_OFF = 2
    const val NO_BUDGET = 3
    const val BUDGET_LOCKED = 4
    const val READ_ONLY = 5
    const val AMOUNT = 6
    const val ACCOUNT = 7
    const val DATE = 8
    const val PAYEE = 9
    const val CLEARED = 10
    const val MONTH = 11
    const val CATEGORY = 12
    const val NOT_ON_DEVICE = 13
    const val SAVE_FAILED = 14
    const val APP_LOCKED = 15
    const val AMOUNTS_HIDDEN = 16
}

enum class AutomationKind { WRITE, READ }

/** The open budget an automation call works on. */
data class AutomationTarget(
    val budgetId: String,
    val encrypted: Boolean,
)

/** The selected budget, once the user's Automation settings allow [kind]; otherwise the reason it can't run. */
fun automationTarget(
    preferences: Map<String, String>,
    kind: AutomationKind,
): AutomationTarget {
    when (kind) {
        AutomationKind.WRITE -> {
            if (!AutomationAccess.writesAllowed(preferences)) {
                throw AutomationException(
                    AutomationErrorCode.WRITES_OFF,
                    "Adding transactions is off. Turn it on in Tandem under Settings → Automation.",
                )
            }
        }

        AutomationKind.READ -> {
            if (!AutomationAccess.readsAllowed(preferences)) {
                throw AutomationException(
                    AutomationErrorCode.READS_OFF,
                    "Reading budget numbers is off. Turn it on in Tandem under Settings → Automation.",
                )
            }
        }
    }
    val budgetId =
        preferences["selectedBudgetId"]?.takeIf(String::isNotBlank)
            ?: throw AutomationException(AutomationErrorCode.NO_BUDGET, "No budget is open. Open a budget in Tandem first.")
    if (kind == AutomationKind.WRITE && preferences["readOnlyMode"] == "true") {
        throw AutomationException(
            AutomationErrorCode.READ_ONLY,
            "Lock editing is on in Tandem. Turn it off under Settings → Security and privacy to add transactions.",
        )
    }
    if (kind == AutomationKind.READ) requireAmountsVisible(preferences)
    return AutomationTarget(budgetId, preferences["selectedBudgetEncrypted"] == "true")
}

/**
 * The budget whose account and category names may be offered: only once either opt-in is on, and for an encrypted
 * budget only when [encryptedReadable], because the person let automation read it while it's locked.
 */
fun automationNamesTarget(
    preferences: Map<String, String>,
    encryptedReadable: Boolean = false,
): AutomationTarget? {
    if (!AutomationAccess.writesAllowed(preferences) && !AutomationAccess.readsAllowed(preferences)) return null
    val budgetId = preferences["selectedBudgetId"]?.takeIf(String::isNotBlank) ?: return null
    val encrypted = preferences["selectedBudgetEncrypted"] == "true"
    return AutomationTarget(budgetId, encrypted).takeUnless { encrypted && !encryptedReadable }
}

/** Reads stay closed whenever the home-screen widget would hide amounts too. */
private fun requireAmountsVisible(preferences: Map<String, String>) {
    if (preferences["biometricLockEnabled"] == "true") {
        throw AutomationException(AutomationErrorCode.APP_LOCKED, "Tandem is locked. Turn off app lock in Tandem to read budget numbers.")
    }
    if (preferences["privacyModeEnabled"] == "true") {
        throw AutomationException(
            AutomationErrorCode.AMOUNTS_HIDDEN,
            "Amounts are hidden in Tandem. Turn off Hide budget amounts to read them.",
        )
    }
}

/**
 * The database [target] lives in. An encrypted budget opens only with the password kept for background sync; without
 * it the call fails, because a background call can never ask for the password.
 */
suspend fun automationDatabase(
    target: AutomationTarget,
    mainDatabase: TandemDatabase,
    backgroundKey: String?,
    openVault: suspend (budgetId: String, password: String) -> TandemDatabase?,
): TandemDatabase {
    if (!target.encrypted) return mainDatabase
    val password = BackgroundBudgetKey.decode(backgroundKey)?.takeIf { it.first == target.budgetId }?.second
    return password?.let { openVault(target.budgetId, it) }
        ?: throw AutomationException(
            AutomationErrorCode.BUDGET_LOCKED,
            "This encrypted budget is locked. In Tandem, turn on Schedule background sync for it so automation apps can use it while it’s locked.",
        )
}

private val UNSET_VARIABLE = Regex("^%[A-Za-z0-9_]+$")

/** A caller's text input, or null when it's blank or a Tasker variable that was never set, such as `%payee`. */
fun automationInput(value: String?): String? = value?.trim()?.takeUnless { it.isEmpty() || UNSET_VARIABLE.matches(it) }
