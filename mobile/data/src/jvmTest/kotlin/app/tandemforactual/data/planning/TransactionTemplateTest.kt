package app.tandemforactual.data.planning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class TransactionTemplateTest {
    @Test
    fun keepsTemplatesAsJson() {
        val coffee = TransactionTemplate("t1", "p1", "Corner Café", 450, false, "c1", "Dining", "a1")
        assertEquals(coffee, TransactionTemplate.fromJson("t1", coffee.toJson()))
        val bare = TransactionTemplate("t2", null, "Salary", 300_000, true, null, null, null)
        assertEquals(bare, TransactionTemplate.fromJson("t2", bare.toJson()))
        assertNull(TransactionTemplate.fromJson("t3", JsonObject(emptyMap())))
        assertNull(TransactionTemplate.fromJson("t4", JsonObject(mapOf("payeeName" to JsonPrimitive(" ")))))
        assertNull(TransactionTemplate.fromJson("t5", JsonObject(mapOf("payeeName" to JsonPrimitive("Shop")))))
    }
}
