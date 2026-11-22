package app.tandemforactual.data.planning

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedBillTest {
    private val alex = SharedBillMember("Alex", "alex", 4_000, "Zelle from Alex")
    private val sam = SharedBillMember("Sam", "sam", 4_000, "VENMO SAM", ruleCreated = true)
    private val plan = SharedBill("b1", "AT&T plan", "att-plan", listOf(alex, sam), dueDay = 10, startMonth = "2026-01")

    private fun paid(
        date: String,
        amount: Long,
        notes: String,
    ) = TaggedPayment("t-$date-$amount", date, amount, notes)

    @Test
    fun roundTripsThroughJson() {
        assertEquals(plan, SharedBill.fromJson("b1", plan.toJson()))
        assertEquals(SharedBill("b2", "", "", emptyList()), SharedBill.fromJson("b2", JsonObject(emptyMap())))
        assertEquals(
            SharedBill("b3", "x", "x", listOf(SharedBillMember("", "", 0))),
            SharedBill.fromJson(
                "b3",
                JsonObject(
                    mapOf(
                        "name" to JsonPrimitive("x"),
                        "tag" to JsonPrimitive("x"),
                        "members" to JsonArray(listOf(JsonObject(emptyMap()), JsonPrimitive(1))),
                    ),
                ),
            ),
        )
        assertEquals("at-t-family-plan", SharedBill.tagFor("AT&T Family Plan!"))
        assertEquals("shared", SharedBill.tagFor("&&"))
    }

    @Test
    fun matchesWholeTagsOnly() {
        assertTrue(paid("2026-11-02", 1, "Zelle #att-plan #alex").hasTag("att-plan"))
        assertTrue(paid("2026-11-02", 1, "#alex").hasTag("alex"))
        assertFalse(paid("2026-11-02", 1, "#att-plan2 #alexa").hasTag("att-plan"))
        assertFalse(paid("2026-11-02", 1, "#alexa").hasTag("alex"))
    }

    @Test
    fun showsWhoPaidEachMonth() {
        val payments =
            listOf(
                paid("2026-11-03", 4_000, " #att-plan #alex"),
                paid("2026-11-04", 1_500, " #att-plan #sam"),
                paid("2026-10-03", 4_000, " #att-plan #alex"),
                paid("2026-10-05", 4_000, " #att-plan #sam"),
                paid("2026-09-05", 4_000, " #att-plan #alex"),
                paid("2026-11-05", 9_000, " #other #sam"),
            )
        val november = SharedBills.month(plan, payments, "2026-11", "2026-11-20")
        assertEquals(listOf(ShareState.PAID, ShareState.PARTLY_PAID), november.map { it.state })
        assertEquals(1_500, november[1].paidMinor)
        val early = SharedBills.month(plan, emptyList(), "2026-11", "2026-11-05")
        assertEquals(listOf(ShareState.NOT_YET_DUE, ShareState.NOT_YET_DUE), early.map { it.state })
        assertEquals(ShareState.MISSING, SharedBills.month(plan, emptyList(), "2026-10", "2026-11-05").first().state)
        assertEquals(ShareState.MISSING, SharedBills.month(plan.copy(dueDay = null), emptyList(), "2026-11", "2026-11-01").first().state)
        val started = plan.copy(startMonth = "2026-10")
        assertEquals(ShareState.NOT_TRACKED, SharedBills.month(started, emptyList(), "2026-09", "2026-11-20").first().state)
        assertEquals(ShareState.PAID, SharedBills.month(started, payments, "2026-09", "2026-11-20").first().state)
        assertEquals(ShareState.MISSING, SharedBills.month(started, emptyList(), "2026-10", "2026-11-20").first().state)
        val months = listOf("2026-09", "2026-10", "2026-11")
        assertEquals(2, SharedBills.streak(plan, alex, payments, months, "2026-11-20"))
        assertEquals(1, SharedBills.streak(plan, sam, payments, months, "2026-11-20"))
    }

    @Test
    fun buildsARuleThatTagsThePersonsPayments() {
        val rule = SharedBills.rule(plan, alex)
        assertEquals("post", rule.getValue("stage").jsonPrimitive.content)
        val conditions = rule.getValue("conditions").jsonArray
        assertEquals(
            "Zelle from Alex",
            conditions[0]
                .jsonObject
                .getValue("text")
                .jsonPrimitive.content,
        )
        assertEquals(
            "true",
            conditions[1]
                .jsonObject
                .getValue("inflow")
                .jsonPrimitive.content,
        )
        assertEquals(
            " #att-plan #alex",
            rule
                .getValue("actions")
                .jsonArray[0]
                .jsonObject
                .getValue("text")
                .jsonPrimitive.content,
        )
    }
}
