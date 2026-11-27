package app.tandemforactual.data.inbox

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.planning.PlanningKeys
import app.tandemforactual.data.store.AppPreferencesStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * A budget's inbox keys and the entries already added, kept inside its encrypted vault. The newest pair is the one
 * published; older pairs stay a while so entries sealed just before a change still open.
 */
@OptIn(ExperimentalEncodingApi::class)
class InboxKeyring(
    database: TandemDatabase,
    private val budgetId: String,
) {
    private val store = AppPreferencesStore(database)
    private val keysName = "internal:inbox-keys-v1:$budgetId"
    private val appliedName = "internal:inbox-applied-v1:$budgetId"

    suspend fun keys(): List<InboxKeyPair> = stored().map { it.pair }

    /** The pair to publish: the newest one, or a new one when there's none yet or the budget password changed. */
    suspend fun ensure(password: String): InboxKeyPair {
        val check = passwordCheck(password)
        val stored = stored()
        stored.lastOrNull()?.takeIf { it.check == check }?.let { return it.pair }
        val pair = SealedBox.newKeyPair()
        save((stored + StoredPair(pair, check)).takeLast(MAX_KEYS))
        return pair
    }

    suspend fun isApplied(entryId: String): Boolean = entryId in applied()

    suspend fun markApplied(entryId: String) {
        store.putValue(appliedName, JsonArray((applied() + entryId).takeLast(MAX_APPLIED).map(::JsonPrimitive)).toString())
    }

    private suspend fun applied(): List<String> =
        parse(appliedName).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    private suspend fun stored(): List<StoredPair> =
        parse(keysName).mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null

            fun bytes(name: String) = (item[name] as? JsonPrimitive)?.contentOrNull?.let { runCatching { Base64.decode(it) }.getOrNull() }
            val publicKey = bytes("public") ?: return@mapNotNull null
            val privateKey = bytes("private") ?: return@mapNotNull null
            val check = (item["check"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            StoredPair(InboxKeyPair(SealedBox.keyId(publicKey), publicKey, privateKey), check)
        }

    private suspend fun save(pairs: List<StoredPair>) {
        val json =
            JsonArray(
                pairs.map {
                    buildJsonObject {
                        put("public", Base64.encode(it.pair.publicKey))
                        put("private", Base64.encode(it.pair.privateKey))
                        put("check", it.check)
                    }
                },
            )
        store.putValue(keysName, json.toString())
    }

    private suspend fun parse(name: String): JsonArray =
        store.read()[name]?.let { runCatching { Json.parseToJsonElement(it) as? JsonArray }.getOrNull() } ?: JsonArray(emptyList())

    private fun passwordCheck(password: String): String =
        PlanningKeys.hkdf(password.encodeToByteArray(), CHECK_SALT, budgetId.encodeToByteArray(), CHECK_BYTES).toHex()

    private class StoredPair(
        val pair: InboxKeyPair,
        val check: String,
    )

    private companion object {
        const val MAX_KEYS = 4
        const val MAX_APPLIED = 1000
        const val CHECK_BYTES = 16
        val CHECK_SALT = "tandem-inbox-password-check".encodeToByteArray()
    }
}

/** Each budget's inbox public key, kept on the device in the clear so automation can seal entries while it's locked. */
@OptIn(ExperimentalEncodingApi::class)
object InboxPublicKeys {
    private const val PREFIX = "internal:inbox-public-key-v1:"

    private fun name(budgetId: String) = PREFIX + budgetId

    suspend fun publish(
        database: TandemDatabase,
        budgetId: String,
        pair: InboxKeyPair,
    ) {
        val store = AppPreferencesStore(database)
        val value = Base64.encode(pair.publicKey)
        if (store.read()[name(budgetId)] != value) store.putValue(name(budgetId), value)
    }

    suspend fun read(
        database: TandemDatabase,
        budgetId: String,
    ): ByteArray? =
        AppPreferencesStore(database)
            .read()[name(budgetId)]
            ?.takeIf(String::isNotEmpty)
            ?.let { runCatching { Base64.decode(it) }.getOrNull() }

    suspend fun forget(
        database: TandemDatabase,
        budgetId: String,
    ) {
        AppPreferencesStore(database).putValue(name(budgetId), "")
    }

    suspend fun forgetAll(database: TandemDatabase) {
        val store = AppPreferencesStore(database)
        store.read().filter { (name, value) -> name.startsWith(PREFIX) && value.isNotEmpty() }.keys.forEach { store.putValue(it, "") }
    }
}
