package app.tandemforactual.data.oracle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

data class BudgetScenario(
    val name: String,
    val tracking: Boolean,
    val months: List<String>,
    val accounts: List<Account>,
    val groups: List<Group>,
    val transactions: List<Transaction>,
    val budgets: List<Allocation>,
    val carryover: List<Carryover>,
    val holds: List<Hold>,
) {
    data class Account(
        val name: String,
        val offBudget: Boolean,
    )

    data class Group(
        val name: String,
        val income: Boolean,
        val hidden: Boolean,
        val categories: List<Category>,
    )

    data class Category(
        val name: String,
        val hidden: Boolean,
    )

    data class Transaction(
        val account: String,
        val date: String,
        val amount: Long,
        val category: String?,
        val transferTo: String?,
        val splits: List<Split>,
        val deleted: Boolean,
    )

    data class Split(
        val amount: Long,
        val category: String?,
    )

    data class Allocation(
        val month: String,
        val category: String,
        val amount: Long,
    )

    data class Carryover(
        val from: String,
        val category: String,
        val flag: Boolean,
    )

    data class Hold(
        val month: String,
        val amount: Long,
    )

    fun toJson(): JsonObject =
        obj(
            "name" to JsonPrimitive(name),
            "type" to JsonPrimitive(if (tracking) "tracking" else "envelope"),
            "months" to JsonArray(months.map(::JsonPrimitive)),
            "accounts" to JsonArray(accounts.map { obj("name" to JsonPrimitive(it.name), "offbudget" to JsonPrimitive(it.offBudget)) }),
            "groups" to JsonArray(groups.map { it.toJson() }),
            "transactions" to JsonArray(transactions.map { it.toJson() }),
            "budgets" to
                JsonArray(
                    budgets.map {
                        obj(
                            "month" to JsonPrimitive(it.month),
                            "category" to JsonPrimitive(it.category),
                            "amount" to JsonPrimitive(it.amount),
                        )
                    },
                ),
            "carryover" to
                JsonArray(
                    carryover.map {
                        obj("from" to JsonPrimitive(it.from), "category" to JsonPrimitive(it.category), "flag" to JsonPrimitive(it.flag))
                    },
                ),
            "holds" to JsonArray(holds.map { obj("month" to JsonPrimitive(it.month), "amount" to JsonPrimitive(it.amount)) }),
        )

    companion object {
        fun parseAll(text: String): List<BudgetScenario> = Json.parseToJsonElement(text).jsonArray.map { parse(it.jsonObject) }

        fun parse(o: JsonObject): BudgetScenario =
            BudgetScenario(
                name = o.text("name")!!,
                tracking = o.text("type") == "tracking",
                months = o.list("months").map { it.jsonPrimitive.content },
                accounts = o.list("accounts").map { Account(it.text("name")!!, it.flag("offbudget")) },
                groups =
                    o.list("groups").map { g ->
                        Group(
                            g.text("name")!!,
                            g.flag("income"),
                            g.flag("hidden"),
                            g.list("categories").map { Category(it.text("name")!!, it.flag("hidden")) },
                        )
                    },
                transactions =
                    o.list("transactions").map { t ->
                        Transaction(
                            t.text("account")!!,
                            t.text("date")!!,
                            t.amount(),
                            t.text("category"),
                            t.text("transferTo"),
                            t.list("splits").map { Split(it.amount(), it.text("category")) },
                            t.flag("deleted"),
                        )
                    },
                budgets = o.list("budgets").map { Allocation(it.text("month")!!, it.text("category")!!, it.amount()) },
                carryover = o.list("carryover").map { Carryover(it.text("from")!!, it.text("category")!!, it.flag("flag")) },
                holds = o.list("holds").map { Hold(it.text("month")!!, it.amount()) },
            )

        private fun JsonElement.text(key: String) = (jsonObject[key] as? JsonPrimitive)?.content

        private fun JsonElement.flag(key: String) = (jsonObject[key] as? JsonPrimitive)?.booleanOrNull ?: false

        private fun JsonElement.amount() = jsonObject.getValue("amount").jsonPrimitive.long

        private fun JsonElement.list(key: String) = (jsonObject[key] as? JsonArray).orEmpty()
    }
}

private fun obj(vararg pairs: Pair<String, JsonElement?>) =
    JsonObject(
        pairs.filter { it.second != null }.associate {
            it.first to
                it.second!!
        },
    )

private fun BudgetScenario.Group.toJson() =
    obj(
        "name" to JsonPrimitive(name),
        "income" to JsonPrimitive(income).takeIf { income },
        "hidden" to JsonPrimitive(hidden).takeIf { hidden },
        "categories" to
            JsonArray(
                categories.map {
                    obj(
                        "name" to JsonPrimitive(it.name),
                        "hidden" to
                            JsonPrimitive(it.hidden).takeIf { _ ->
                                it.hidden
                            },
                    )
                },
            ),
    )

private fun BudgetScenario.Transaction.toJson() =
    obj(
        "account" to JsonPrimitive(account),
        "date" to JsonPrimitive(date),
        "amount" to JsonPrimitive(amount),
        "category" to category?.let(::JsonPrimitive),
        "transferTo" to transferTo?.let(::JsonPrimitive),
        "deleted" to JsonPrimitive(true).takeIf { deleted },
        "splits" to
            JsonArray(
                splits.map {
                    obj("amount" to JsonPrimitive(it.amount), "category" to it.category?.let(::JsonPrimitive))
                },
            ).takeIf { splits.isNotEmpty() },
    )
