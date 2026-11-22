package app.tandemforactual.ui.core.format

/** Plain words for the bank error codes Actual's providers return. */
internal fun bankFailureLabel(code: String): String =
    when (code.uppercase()) {
        "ITEM_LOGIN_REQUIRED", "INVALID_ACCESS_TOKEN" -> "the bank needs you to sign in again"
        "RATE_LIMIT_EXCEEDED" -> "the bank asked to wait before syncing again"
        "ACCOUNT_NEEDS_ATTENTION" -> "the bank needs attention"
        "NO_ACCOUNTS", "ACCOUNT_MISSING" -> "the bank no longer shares this account"
        else -> code.lowercase().replace('_', ' ')
    }
