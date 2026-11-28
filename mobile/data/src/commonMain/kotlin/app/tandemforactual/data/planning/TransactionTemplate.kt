package app.tandemforactual.data.planning

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A one-tap entry: the payee, amount, category and account a new transaction starts with. Names show when ids are gone. */
data class TransactionTemplate(
    val id: String,
    val payeeId: String?,
    val payeeName: String,
    val amountMinor: Long,
    val income: Boolean,
    val categoryId: String?,
    val categoryName: String?,
    val accountId: String?,
) {
    fun toJson(): JsonObject =
        jsonOf(
            "payeeId" to payeeId.json(),
            "payeeName" to JsonPrimitive(payeeName),
            "amountMinor" to JsonPrimitive(amountMinor),
            "income" to JsonPrimitive(income),
            "categoryId" to categoryId.json(),
            "categoryName" to categoryName.json(),
            "accountId" to accountId.json(),
        )

    companion object {
        const val TYPE = "planning.transaction_template"

        fun fromJson(
            id: String,
            body: JsonObject,
        ): TransactionTemplate? {
            val payee = body.text("payeeName")?.takeIf { it.isNotBlank() } ?: return null
            val amount = body.long("amountMinor") ?: return null
            return TransactionTemplate(
                id,
                body.text("payeeId"),
                payee,
                amount,
                body.text("income") == "true",
                body.text("categoryId"),
                body.text("categoryName"),
                body.text("accountId"),
            )
        }
    }
}
