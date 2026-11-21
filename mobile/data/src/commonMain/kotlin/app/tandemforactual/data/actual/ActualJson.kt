package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** A JSON number as JavaScript prints it: integral values without a fraction. */
internal fun jsonNumber(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is Long -> JsonPrimitive(value)
        is Double ->
            if (value == kotlin.math.floor(value) &&
                kotlin.math.abs(value) < 9.007199254740992E15
            ) {
                JsonPrimitive(value.toLong())
            } else {
                JsonPrimitive(value)
            }
        else -> JsonPrimitive(value.toString())
    }

internal fun jsonString(value: Any?): JsonElement = if (value == null) JsonNull else JsonPrimitive(value.toString())

internal fun jsonBool(value: Any?): JsonPrimitive = JsonPrimitive(truthy(value))

internal fun truthy(value: Any?): Boolean =
    when (value) {
        null -> false
        is Boolean -> value
        is Long -> value != 0L
        is Double -> value != 0.0
        is String -> value.isNotEmpty()
        else -> true
    }

/** `JSON.stringify` with object keys sorted, matching the API's `canonicalJson`. */
internal fun canonicalJson(value: JsonElement): String =
    when (value) {
        is JsonObject -> value.keys.sorted().joinToString(",", "{", "}") { quoteJson(it) + ":" + canonicalJson(value.getValue(it)) }
        is JsonArray -> value.joinToString(",", "[", "]") { canonicalJson(it) }
        else -> stringifyJson(value)
    }

/** `JSON.stringify` keeping key order. */
internal fun stringifyJson(value: JsonElement): String =
    when (value) {
        JsonNull -> "null"
        is JsonObject -> value.entries.joinToString(",", "{", "}") { (k, v) -> quoteJson(k) + ":" + stringifyJson(v) }
        is JsonArray -> value.joinToString(",", "[", "]") { stringifyJson(it) }
        is JsonPrimitive ->
            when {
                value.isString -> quoteJson(value.content)
                value.booleanOrNull != null -> value.content
                else -> value.doubleOrNull?.let { jsNumberString(it, value.content) } ?: value.content
            }
    }

private fun jsNumberString(
    value: Double,
    raw: String,
): String = if (raw.none { it == '.' || it == 'e' || it == 'E' }) raw else formatNumber(value)

internal fun quoteJson(text: String): String {
    val out = StringBuilder(text.length + 2).append('"')
    for ((i, c) in text.withIndex()) {
        val lone =
            (c.isHighSurrogate() && (i + 1 >= text.length || !text[i + 1].isLowSurrogate())) ||
                (c.isLowSurrogate() && (i == 0 || !text[i - 1].isHighSurrogate()))
        when {
            c == '"' -> out.append("\\\"")
            c == '\\' -> out.append("\\\\")
            c == '\b' -> out.append("\\b")
            c == '\u000C' -> out.append("\\f")
            c == '\n' -> out.append("\\n")
            c == '\r' -> out.append("\\r")
            c == '\t' -> out.append("\\t")
            c < ' ' || lone -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> out.append(c)
        }
    }
    return out.append('"').toString()
}
