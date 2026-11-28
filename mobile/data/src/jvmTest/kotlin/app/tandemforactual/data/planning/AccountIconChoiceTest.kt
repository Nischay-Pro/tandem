package app.tandemforactual.data.planning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class AccountIconChoiceTest {
    @Test
    fun keepsAnAccountsIconAsJson() {
        val choice = AccountIconChoice("checking", "piggy-bank")
        assertEquals("icon:checking", choice.id)
        assertEquals(choice, AccountIconChoice.fromJson(choice.toJson()))
        assertNull(AccountIconChoice.fromJson(JsonObject(emptyMap())))
        assertNull(AccountIconChoice.fromJson(JsonObject(mapOf("accountId" to JsonPrimitive("checking")))))
        assertNull(AccountIconChoice.fromJson(JsonObject(mapOf("accountId" to JsonPrimitive(" "), "icon" to JsonPrimitive("x")))))
        assertNull(AccountIconChoice.fromJson(JsonObject(mapOf("accountId" to JsonPrimitive("a"), "icon" to JsonPrimitive(" ")))))
    }
}
