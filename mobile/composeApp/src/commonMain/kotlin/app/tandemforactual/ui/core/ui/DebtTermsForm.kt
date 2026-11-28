package app.tandemforactual.ui.core.ui

import app.tandemforactual.data.planning.DebtKind
import app.tandemforactual.data.planning.DebtTerms
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.parseAmountMinorUnits

/** What the debt terms sheet holds as typed. */
internal data class DebtTermsForm(
    val apr: String,
    val minimum: String,
    val dueDay: String,
    val closeDay: String,
    val limit: String,
    val kind: DebtKind,
    val paysInFull: Boolean,
    val keepPrivate: Boolean,
)

/** The terms the form describes, or the first thing to fix. Closing day and limit only apply to cards. */
internal fun DebtTermsForm.toTerms(
    id: String,
    accountId: String,
    settings: BudgetDisplaySettings?,
): Result<DebtTerms> {
    val card = kind == DebtKind.CARD
    val rate = apr.takeIf { it.isNotBlank() }?.let(::parseRateBasisPoints)
    val payment = minimum.takeIf { it.isNotBlank() }?.let { parseAmountMinorUnits(it, settings) }
    val due = dueDay.toIntOrNull()
    val close = closeDay.takeIf { card }?.toIntOrNull()
    val creditLimit = limit.takeIf { card && it.isNotBlank() }?.let { parseAmountMinorUnits(it, settings) }
    val problem =
        when {
            apr.isNotBlank() && rate == null -> "Enter the APR as a percentage, like 19.99."
            minimum.isNotBlank() && (payment == null || payment < 0) -> "Enter the minimum payment."
            dueDay.isNotBlank() && (due == null || due !in 1..31) -> "Enter a day between 1 and 31."
            card && closeDay.isNotBlank() && (close == null || close !in 1..31) -> "Enter a closing day between 1 and 31."
            card && limit.isNotBlank() && (creditLimit == null || creditLimit <= 0) -> "Enter the credit limit."
            else -> null
        }
    if (problem != null) return Result.failure(IllegalArgumentException(problem))
    return Result.success(
        DebtTerms(
            id,
            accountId,
            rate,
            payment,
            due,
            kind = kind,
            paysInFull = card && paysInFull,
            shared = !keepPrivate,
            statementCloseDay = close,
            creditLimitMinor = creditLimit,
        ),
    )
}

/** What's left to spend on a card with [limitMinor] while [owedMinor] is owed, and the share of the limit in use. */
internal fun creditUse(
    owedMinor: Long,
    limitMinor: Long,
): Pair<Long, Int> {
    val owed = owedMinor.coerceAtLeast(0)
    return (limitMinor - owed).coerceAtLeast(0) to (owed * 100 / limitMinor).toInt()
}
