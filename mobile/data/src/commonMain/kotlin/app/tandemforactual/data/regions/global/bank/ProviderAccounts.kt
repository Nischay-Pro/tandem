package app.tandemforactual.data.regions.global.bank

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull

/** The `accounts` array most providers return, each mapped by [read]. */
internal fun JsonObject.accounts(read: (JsonObject) -> BankProviderAccount): List<BankProviderAccount> =
    (this["accounts"] as? JsonArray).orEmpty().map { read(it.jsonObject) }
