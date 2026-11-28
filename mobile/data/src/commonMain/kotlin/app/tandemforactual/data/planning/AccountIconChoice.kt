package app.tandemforactual.data.planning

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The symbol the person picked to show for one Actual account, by its name in the app's icon set. */
data class AccountIconChoice(
    val accountId: String,
    val icon: String,
) {
    val id: String get() = id(accountId)

    fun toJson(): JsonObject = jsonOf("accountId" to JsonPrimitive(accountId), "icon" to JsonPrimitive(icon))

    companion object {
        const val TYPE = "planning.account_icon"

        fun id(accountId: String) = "icon:$accountId"

        fun fromJson(body: JsonObject): AccountIconChoice? {
            val accountId = body.text("accountId")?.takeIf { it.isNotBlank() } ?: return null
            val icon = body.text("icon")?.takeIf { it.isNotBlank() } ?: return null
            return AccountIconChoice(accountId, icon)
        }
    }
}
