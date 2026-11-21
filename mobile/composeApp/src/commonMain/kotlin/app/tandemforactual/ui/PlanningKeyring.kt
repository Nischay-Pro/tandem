package app.tandemforactual.ui

import app.tandemforactual.data.planning.PlanningCipher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Each budget's random planning key, kept in the Keystore or Keychain-backed [store]. Without a store, keys last only as
 * long as the process, which is what tests want.
 */
@OptIn(ExperimentalEncodingApi::class)
internal class PlanningKeyring(
    private val store: DeviceCredentialStore?,
) {
    private var memory = emptyMap<String, String>()

    private fun read(): Map<String, String> =
        store?.let { stored ->
            stored.read()?.let { json -> Json.parseToJsonElement(json).jsonObject.mapValues { it.value.jsonPrimitive.content } }.orEmpty()
        } ?: memory

    private fun write(keys: Map<String, String>) {
        if (store == null) memory = keys else store.write(JsonObject(keys.mapValues { JsonPrimitive(it.value) }).toString())
    }

    fun cipherFor(budgetId: String): PlanningCipher {
        val keys = read()
        val key =
            keys[budgetId]?.let(Base64::decode) ?: PlanningCipher.newKey().also { write(keys + (budgetId to Base64.encode(it))) }
        return PlanningCipher(key)
    }

    fun forget(budgetId: String) {
        val keys = read()
        if (budgetId in keys) write(keys - budgetId)
    }
}
