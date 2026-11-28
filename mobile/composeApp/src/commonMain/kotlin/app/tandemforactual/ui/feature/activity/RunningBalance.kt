package app.tandemforactual.ui.feature.activity

/** The account's balance after each transaction, from its balance now and its amounts listed newest first. */
internal fun runningBalances(
    amountsNewestFirst: List<Long>,
    balanceNow: Long,
): List<Long> = amountsNewestFirst.runningFold(balanceNow) { balance, amount -> balance - amount }.dropLast(1)
