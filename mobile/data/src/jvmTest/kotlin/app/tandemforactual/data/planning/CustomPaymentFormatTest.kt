package app.tandemforactual.data.planning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonObject

class CustomPaymentFormatTest {
    @Test
    fun keepsFormatsAsJson() {
        val format = CustomPaymentFormat("f1", "My bank", "spent (?<amount>[\\d.]+)", "You spent 12.40")
        assertEquals(format, CustomPaymentFormat.fromJson("f1", format.toJson()))
        assertEquals(CustomPaymentFormat("f2", "", "", ""), CustomPaymentFormat.fromJson("f2", JsonObject(emptyMap())))
    }
}
