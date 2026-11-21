package app.tandemforactual.data.actual

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Actual's trinary radix trie of message timestamps, keyed by minutes in base 3. Each node's hash is the XOR of the
 * hashes beneath it, kept as a signed 32-bit integer like JavaScript's `^` operator.
 */
data class MerkleTrie(
    val hash: Int = 0,
    val children: Map<Char, MerkleTrie> = emptyMap(),
) {
    fun insert(timestamp: ActualTimestamp): MerkleTrie {
        val hash = timestamp.hash().toInt()
        val key = (timestamp.millis / 1000 / 60).toString(3)
        return copy(hash = this.hash xor hash).insertKey(key, hash)
    }

    private fun insertKey(
        key: String,
        hash: Int,
    ): MerkleTrie {
        if (key.isEmpty()) return this
        val c = key[0]
        val n = children[c] ?: MerkleTrie()
        val child = n.insertKey(key.substring(1), hash).copy(hash = n.hash xor hash)
        return copy(children = children + (c to child))
    }

    /** Keeps the newest [n] branches at every level, as Actual does before storing or sending a trie. */
    fun prune(n: Int = 2): MerkleTrie {
        if (hash == 0) return this
        val kept = children.keys.sorted().takeLast(n)
        return MerkleTrie(hash, kept.associateWith { children.getValue(it).prune(n) })
    }

    fun toJson(): JsonObject =
        buildJsonObject {
            children.keys.sorted().forEach { put(it.toString(), children.getValue(it).toJson()) }
            put("hash", JsonPrimitive(hash))
        }

    companion object {
        fun fromJson(json: String): MerkleTrie = fromJson(Json.parseToJsonElement(json).jsonObject)

        fun fromJson(json: JsonObject): MerkleTrie =
            MerkleTrie(
                hash = (json["hash"] as? JsonPrimitive)?.intOrNull ?: 0,
                children =
                    json
                        .filterKeys { it in TRIE_KEYS }
                        .mapKeys { it.key[0] }
                        .mapValues { (_, value: JsonElement) -> fromJson(value.jsonObject) },
            )

        /** The earliest time at which two tries may differ, or null when they match. */
        fun diff(
            first: MerkleTrie,
            second: MerkleTrie,
        ): Long? {
            if (first.hash == second.hash) return null
            var node1 = first
            var node2 = second
            var key = ""
            while (true) {
                val keys = (node1.children.keys + node2.children.keys).sorted()
                var diffKey: Char? = null
                for (k in keys) {
                    val next1 = node1.children[k]
                    val next2 = node2.children[k]
                    if (next1 == null || next2 == null) break
                    if (next1.hash != next2.hash) {
                        diffKey = k
                        break
                    }
                }
                if (diffKey == null) return keyToTimestamp(key)
                key += diffKey
                node1 = node1.children[diffKey] ?: MerkleTrie()
                node2 = node2.children[diffKey] ?: MerkleTrie()
            }
        }

        fun keyToTimestamp(key: String): Long = key.padEnd(16, '0').toLong(3) * 1000 * 60

        private val TRIE_KEYS = setOf("0", "1", "2")
    }
}
