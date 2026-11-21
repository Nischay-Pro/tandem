package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActualBudgetKeyTest {
    private fun b64(value: String) = Base64.getDecoder().decode(value)

    private val key = ActualBudgetKey.derive("key-1", "budget-secret", "c2FsdC1mb3ItdGFuZGVt")

    @Test
    fun matchesNodeCrypto() {
        assertEquals("BYzuaAtJ+gCvEI2r8MSP5oDKG0MCicdzp3u7noaAnoo=", key.base64)
        assertEquals("key-1", key.keyId)
        assertEquals(
            "BYzuaAtJ+gCvEI2r8MSP5oDKG0MCicdzp3u7noaAnoo=",
            java.util.Base64
                .getEncoder()
                .encodeToString(key.secret),
        )
        val sealed = EncryptedData(b64("AAECAwQFBgcICQoL"), b64("enrpLX31X8XrJ/bPJQxKkA=="), b64("kqk5F9rPn/XdOTg4p9mtIq2eDg=="))
        assertEquals("tandem test message", key.decrypt(sealed).decodeToString())
        val meta =
            buildJsonObject {
                put("iv", "AAECAwQFBgcICQoL")
                put("authTag", "enrpLX31X8XrJ/bPJQxKkA==")
            }
        assertEquals("tandem test message", key.decrypt(sealed.data, meta).decodeToString())
        assertFailsWith<Exception> { ActualBudgetKey.derive("key-1", "wrong", "c2FsdC1mb3ItdGFuZGVt").decrypt(sealed) }
    }

    @Test
    fun roundTripsAndChecksTheTestMessage() {
        val sealed = key.encrypt("hello".encodeToByteArray())
        assertEquals(12, sealed.iv.size)
        assertEquals(16, sealed.authTag.size)
        assertContentEquals("hello".encodeToByteArray(), ActualBudgetKey.fromBase64("key-1", key.base64).decrypt(sealed))
        val enc = Base64.getEncoder()
        val test =
            buildJsonObject {
                put("value", enc.encodeToString(sealed.data))
                put(
                    "meta",
                    buildJsonObject {
                        put("keyId", "key-1")
                        put("iv", enc.encodeToString(sealed.iv))
                        put("authTag", enc.encodeToString(sealed.authTag))
                    },
                )
            }.toString()
        assertTrue(key.opens(test))
        assertFalse(ActualBudgetKey.derive("key-1", "wrong", "c2FsdC1mb3ItdGFuZGVt").opens(test))
        assertFalse(key.opens("not json"))
        assertFalse(key.opens(buildJsonObject { put("value", JsonPrimitive("")) }.toString()))
    }
}
