package app.tandemforactual.ui.core.format

/** Plain words for the bank error codes Actual's providers return. */
internal fun bankFailureLabel(code: String): String =
    when (code.uppercase()) {
        "ITEM_LOGIN_REQUIRED", "INVALID_ACCESS_TOKEN" -> "the bank needs you to sign in again"
        "RATE_LIMIT_EXCEEDED" -> "the bank asked to wait before syncing again"
        "ACCOUNT_NEEDS_ATTENTION" -> "the bank needs attention"
        "NO_ACCOUNTS", "ACCOUNT_MISSING" -> "the bank no longer shares this account"
        "UNREADABLE_TRANSACTION" -> "a transaction from the bank couldn’t be read"
        else -> code.lowercase().replace('_', ' ')
    }

/** Why a linked account's last sync failed, and what to do about it; [browser] banks are relinked by signing in again. */
internal fun bankFailureMessage(
    code: String,
    browser: Boolean,
): String =
    "Last sync failed because ${bankFailureLabel(code)}. " +
        when {
            code.uppercase() == "UNREADABLE_TRANSACTION" ->
                "Tandem skipped this account’s sync so nothing wrong was saved. " +
                    "Try again later; if it keeps happening, sync it from Actual’s web app."
            browser -> "Sign in at the bank again to reconnect it."
            else -> "Reconnect it in Actual’s web app, or link a different account."
        }
