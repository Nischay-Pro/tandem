package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualRecord
import app.tandemforactual.data.actual.ActualSnapshot
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.BudgetSyncProtocolException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BudgetSyncJsonTest {
    private fun record(
        type: String,
        body: String,
    ) = ActualRecord(type, "id", Json.parseToJsonElement(body).jsonObject, "r1")

    private fun project(vararg records: ActualRecord) =
        BudgetSyncJson
            .projectDirectSnapshot(
                ActualSnapshot(records.toList(), BudgetDisplaySettings("USD", "$", 2, null, false, null, false)),
                "group",
                "gen",
                "1",
            ).records

    @Test
    fun projectsScheduleAmountsAndRejectsMalformedFields() {
        assertEquals(1_500L, project(record("schedule", """{"amount":{"num1":1000,"num2":2000}}""")).single().amountMinor)
        assertNull(project(record("schedule", """{"amount":{"num1":1000}}""")).single().amountMinor)
        assertNull(project(record("schedule", """{"amount":{"num2":1000}}""")).single().amountMinor)
        assertEquals(-5L, project(record("schedule", """{"amount":-5}""")).single().amountMinor)
        assertFailsWith<BudgetSyncProtocolException> { project(record("payee", """{"name":5}""")) }
        assertFailsWith<BudgetSyncProtocolException> { project(record("transaction", """{"amount":"5"}""")) }
        assertFailsWith<BudgetSyncProtocolException> { project(record("transaction", """{"amount":[5]}""")) }
        assertFailsWith<BudgetSyncProtocolException> { project(record("transaction", """{"amount":1.5}""")) }
        assertEquals(
            "Rent",
            BudgetSyncJson.directTransaction(record("transaction", """{"name":"Rent","amount":7}""")).displayMetadata?.displayName,
        )
        assertFailsWith<BudgetSyncProtocolException> { BudgetSyncJson.parseJsonObject("[1]") }
        assertFailsWith<BudgetSyncProtocolException> { BudgetSyncJson.parseJsonObject("{") }
    }
}
