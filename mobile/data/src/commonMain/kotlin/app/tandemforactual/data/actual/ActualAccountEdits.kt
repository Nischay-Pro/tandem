package app.tandemforactual.data.actual

fun ActualBudgetEdits.createAccount(
    name: String,
    offBudget: Boolean,
    accountGroupId: String?,
): String =
    writer.batch {
        if (accountGroupId != null &&
            db.first("SELECT id FROM account_groups WHERE id = ? AND tombstone = 0", accountGroupId) == null
        ) {
            fail("Choose an account group in this budget")
        }
        val peers = db.query("SELECT * FROM accounts WHERE offbudget = ? ORDER BY sort_order, name", if (offBudget) 1L else 0L)
        val (_, sortOrder) = shove(orders(peers), null)
        val id =
            writer.insert(
                "accounts",
                linkedMapOf(
                    "name" to name,
                    "offbudget" to offBudget,
                    "closed" to false,
                    "sort_order" to sortOrder,
                ),
            )
        insertPayee(linkedMapOf("name" to "", "transfer_acct" to id))
        if (accountGroupId != null) writer.update("accounts", id, mapOf("account_group_id" to accountGroupId))
        id
    }

/** Creates a budget account for a bank or card account and links it at once; its first sync sets the starting balance. */
fun ActualBudgetEdits.createLinkedAccount(
    name: String,
    offBudget: Boolean,
    source: String,
    providerAccountId: String,
    bankId: String,
    bankName: String?,
): String =
    writer.batch {
        val id = createAccount(name.trim().ifEmpty { bankName?.trim()?.ifEmpty { null } ?: "Bank account" }, offBudget, null)
        linkBankAccount(id, source, providerAccountId, bankId, bankName)
        id
    }

fun ActualBudgetEdits.renameAccount(
    id: String,
    name: String,
) = writer.batch {
    if (accounts().none { it["id"] == id }) fail("Account does not exist in this budget")
    writer.update("accounts", id, mapOf("name" to name))
}

fun ActualBudgetEdits.reopenAccount(id: String) =
    writer.batch {
        val account = accounts().firstOrNull { it["id"] == id } ?: fail("Account does not exist in this budget")
        if (!truthy(account["closed"])) fail("This account is already open")
        writer.update("accounts", id, mapOf("closed" to 0L))
    }

fun ActualBudgetEdits.closeAccount(
    id: String,
    transferAccountId: String?,
    categoryId: String?,
) = writer.batch {
    val all = accounts()
    if (all.none { it["id"] == id }) fail("Account does not exist in this budget")
    if (transferAccountId != null) {
        val destination = all.firstOrNull { it["id"] == transferAccountId }
        if (destination == null ||
            transferAccountId == id ||
            truthy(destination["closed"])
        ) {
            fail("Choose a different open account in this budget")
        }
    }
    if (categoryId != null && categories().none { it["id"] == categoryId }) fail("Choose a category in this budget")
    val row = db.first("SELECT * FROM accounts WHERE id = ?", id)!!
    if (row["bank"] != null) {
        writer.update(
            "accounts",
            id,
            linkedMapOf(
                "account_id" to null,
                "bank" to null,
                "balance_current" to null,
                "balance_available" to null,
                "balance_limit" to null,
                "account_sync_source" to null,
                "bank_sync_status" to null,
            ),
        )
    }
    val account = db.first("SELECT * FROM accounts WHERE id = ? AND tombstone = 0", id) ?: return@batch
    if (truthy(account["closed"])) return@batch
    val balance =
        (
            db
                .first(
                    "SELECT sum(amount) as balance FROM transactions WHERE acct = ? AND isParent = 0 AND tombstone = 0",
                    id,
                )?.get("balance") as Number?
        )?.toLong()
            ?: 0L
    val count =
        (
            db
                .first(
                    "SELECT count(id) as count FROM transactions WHERE acct = ? AND tombstone = 0",
                    id,
                )?.get("count") as Number
        ).toLong()
    if (count == 0L) {
        writer.delete("accounts", id)
        return@batch
    }
    if (balance != 0L && transferAccountId == null) fail("Choose an account to move this balance before closing it")
    writer.update("accounts", id, mapOf("closed" to 1L))
    if (balance != 0L && transferAccountId != null) {
        val payee = checkNotNull(db.first("SELECT id FROM payees WHERE transfer_acct = ?", transferAccountId)?.get("id"))
        transactions.batchUpdate(
            added =
                listOf(
                    linkedMapOf(
                        "id" to writer.uuid(),
                        "payee" to payee,
                        "amount" to -balance,
                        "account" to id,
                        "date" to today(),
                        "notes" to "Closing account",
                        "category" to categoryId,
                    ),
                ),
        )
    }
}

fun ActualBudgetEdits.moveAccount(
    id: String,
    targetId: String?,
) = writer.batch {
    val all = accounts()
    val account = all.firstOrNull { it["id"] == id } ?: fail("Account does not exist in this budget")
    val target = targetId?.let { t -> all.firstOrNull { it["id"] == t } ?: fail("Choose an account in this budget") }
    if (target?.get("id") == id) fail("Choose a different account position")
    if (target != null &&
        (truthy(target["closed"]) != truthy(account["closed"]) || truthy(target["offbudget"]) != truthy(account["offbudget"]))
    ) {
        fail("Accounts can only be reordered within the same section")
    }
    val peers =
        if (truthy(account["closed"])) {
            db.query("SELECT id, sort_order FROM accounts WHERE closed = 1 ORDER BY sort_order, name")
        } else {
            db.query(
                "SELECT id, sort_order FROM accounts WHERE tombstone = 0 AND offbudget = ? ORDER BY sort_order, name",
                if (truthy(account["offbudget"])) 1L else 0L,
            )
        }
    val (updates, order) = shove(orders(peers), targetId)
    updates.forEach { writer.update("accounts", it.id, mapOf("sort_order" to it.sortOrder)) }
    writer.update("accounts", id, linkedMapOf("sort_order" to order, "account_group_id" to account["account_group_id"]))
}

fun ActualBudgetEdits.accountBalance(
    id: String,
    cutoff: String,
): Long =
    (
        db
            .first(
                "SELECT sum(amount) as balance FROM transactions WHERE acct = ? AND isParent = 0 AND tombstone = 0 AND date <= ?",
                id,
                ActualMonths.dateInt(cutoff),
            )?.get("balance") as Number?
    )?.toLong() ?: 0L

/** Actual's `account-move` with no checks, as the order merge sends it. */
internal fun ActualBudgetEdits.rawMoveAccount(
    id: String,
    targetId: String?,
) {
    val account = db.first("SELECT * FROM accounts WHERE id = ?", id)!!
    val peers =
        if (truthy(account["closed"])) {
            db.query("SELECT id, sort_order FROM accounts WHERE closed = 1 ORDER BY sort_order, name")
        } else {
            db.query(
                "SELECT id, sort_order FROM accounts WHERE tombstone = 0 AND offbudget = ? ORDER BY sort_order, name",
                if (truthy(account["offbudget"])) 1L else 0L,
            )
        }
    val (updates, order) = shove(orders(peers), targetId)
    updates.forEach { writer.update("accounts", it.id, mapOf("sort_order" to it.sortOrder)) }
    writer.update("accounts", id, mapOf("sort_order" to order))
}
