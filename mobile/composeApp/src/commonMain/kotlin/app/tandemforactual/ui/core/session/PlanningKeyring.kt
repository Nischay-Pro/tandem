package app.tandemforactual.ui.core.session

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

    private fun value(name: String): String? = read()[name]

    private fun set(
        name: String,
        value: String?,
    ) {
        val values = read()
        write(if (value == null) values - name else values + (name to value))
    }

    /** The budget's planning key, created the first time it's needed. */
    fun key(budgetId: String): ByteArray =
        value(budgetId)?.let(Base64::decode) ?: PlanningCipher.newKey().also { set(budgetId, Base64.encode(it)) }

    fun cipherFor(budgetId: String): PlanningCipher = PlanningCipher(key(budgetId))

    /** Adopts the key another device shared, after the local records have been re-sealed with it. */
    fun replaceKey(
        budgetId: String,
        key: ByteArray,
    ) = set(budgetId, Base64.encode(key))

    /** The secret that wraps the planning key for a budget without Actual encryption; share it to add a device. */
    fun recoveryKey(budgetId: String): ByteArray =
        value("recovery:$budgetId")?.let(Base64::decode) ?: PlanningCipher.newKey().also { set("recovery:$budgetId", Base64.encode(it)) }

    fun setRecoveryKey(
        budgetId: String,
        key: ByteArray,
    ) = set("recovery:$budgetId", Base64.encode(key))

    /** The recovery key in use before a key replacement, kept until the replacement reaches the server. */
    fun previousRecoveryKey(budgetId: String): ByteArray? = value("previous-recovery:$budgetId")?.let(Base64::decode)

    fun setPreviousRecoveryKey(
        budgetId: String,
        key: ByteArray?,
    ) = set("previous-recovery:$budgetId", key?.let(Base64::encode))

    fun cursor(budgetId: String): Long = value("cursor:$budgetId")?.toLongOrNull() ?: 0

    fun setCursor(
        budgetId: String,
        cursor: Long,
    ) = set("cursor:$budgetId", cursor.toString())

    fun forget(budgetId: String) {
        val values = read()
        val remaining =
            values.filterKeys {
                it != budgetId &&
                    it != "recovery:$budgetId" &&
                    it != "cursor:$budgetId" &&
                    it != "previous-recovery:$budgetId"
            }
        if (remaining.size != values.size) write(remaining)
    }
}

/** Shows a recovery key as eight groups of eight hex digits, easy to read aloud or type. */
internal fun formatRecoveryKey(key: ByteArray): String =
    key.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }.chunked(8).joinToString(" ")

internal fun parseRecoveryKey(text: String): ByteArray? {
    val hex = text.filter { !it.isWhitespace() && it != '-' }.lowercase()
    if (hex.length != 64 || hex.any { it !in "0123456789abcdef" }) return null
    return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
