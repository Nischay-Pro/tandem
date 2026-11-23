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
        val bill = plan.copy(startMonth = "2026-09")
        val payments =
            listOf(
                paid("2026-11-03", 4_000, " #att-plan #alex"),
                paid("2026-11-04", 1_500, " #att-plan #sam"),
                paid("2026-10-03", 4_000, " #att-plan #alex"),
                paid("2026-09-05", 4_000, " #att-plan #sam"),
                paid("2026-10-05", 4_000, " #att-plan #sam"),
                paid("2026-09-05", 4_000, " #att-plan #alex"),
                paid("2026-11-05", 9_000, " #other #sam"),
            )
        val november = SharedBills.month(bill, payments, "2026-11", "2026-11-20")
        assertEquals(listOf(ShareState.PAID, ShareState.PARTLY_PAID), november.map { it.state })
        assertEquals(1_500, november[1].paidMinor)
        val early = SharedBills.month(bill, emptyList(), "2026-11", "2026-11-05")
        assertEquals(listOf(ShareState.NOT_YET_DUE, ShareState.NOT_YET_DUE), early.map { it.state })
        assertEquals(ShareState.MISSING, SharedBills.month(bill, emptyList(), "2026-10", "2026-11-05").first().state)
        assertEquals(ShareState.MISSING, SharedBills.month(bill.copy(dueDay = null), emptyList(), "2026-11", "2026-11-01").first().state)
        assertEquals(ShareState.NOT_TRACKED, SharedBills.month(bill, emptyList(), "2026-08", "2026-11-20").first().state)
        assertEquals(ShareState.MISSING, SharedBills.month(bill.copy(startMonth = null), emptyList(), "2026-10", "2026-11-20")[0].state)
        val months = listOf("2026-09", "2026-10", "2026-11")
        assertEquals(2, SharedBills.streak(bill, alex, payments, months, "2026-11-20"))
        assertEquals(2, SharedBills.streak(bill, sam, payments, months, "2026-11-20"))
    }

    @Test
    fun creditsPaymentsToMonthsInOrder() {
        val bill = plan.copy(startMonth = "2026-09")
        val twoMonths = listOf(paid("2026-09-04", 8_000, "#att-plan #alex"))
        assertEquals(mapOf("2026-09" to 4_000L, "2026-10" to 4_000L), SharedBills.credits(bill, alex, twoMonths))
        assertEquals(ShareState.PAID, SharedBills.month(bill, twoMonths, "2026-10", "2026-10-20").first().state)
        val late = listOf(paid("2026-10-04", 6_000, "#att-plan #alex"))
        assertEquals(mapOf("2026-09" to 4_000L, "2026-10" to 2_000L), SharedBills.credits(bill, alex, late))
        val earlier = listOf(paid("2026-07-30", 4_000, "#att-plan #alex"))
        assertEquals(mapOf("2026-07" to 4_000L), SharedBills.credits(bill, alex, earlier))
        assertEquals(emptyMap(), SharedBills.credits(bill, alex.copy(shareMinor = 0), twoMonths))
        assertEquals(emptyMap(), SharedBills.credits(bill, alex, listOf(paid("2026-09-04", -8_000, "#att-plan #alex"))))
        assertEquals(120, SharedBills.credits(bill, alex, listOf(paid("2026-09-04", 1_000_000, "#att-plan #alex"))).size)
        assertEquals("2026-10", SharedBills.paidThrough(bill, alex, twoMonths, "2026-09-20"))
        assertEquals(null, SharedBills.paidThrough(bill, alex, twoMonths, "2026-11-20"))
        assertEquals(null, SharedBills.paidThrough(bill, alex, emptyList(), "2026-09-20"))
    }

    @Test
    fun waitsForTheEndOfAPersonsCycle() {
        val bill = plan.copy(startMonth = "2026-09", dueDay = null)
        val everyOther = alex.copy(everyMonths = 2)
        val twoMonthly = bill.copy(members = listOf(everyOther))
        assertEquals(ShareState.NOT_YET_DUE, SharedBills.month(twoMonthly, emptyList(), "2026-09", "2026-09-20").first().state)
        assertEquals(ShareState.MISSING, SharedBills.month(twoMonthly, emptyList(), "2026-09", "2026-11-01").first().state)
        assertEquals(ShareState.NOT_YET_DUE, SharedBills.month(twoMonthly, emptyList(), "2026-11", "2026-11-20").first().state)
        assertEquals(everyOther, SharedBillMember.fromJson(everyOther.toJson()))
        assertEquals(12, SharedBillMember.fromJson(alex.copy(everyMonths = 40).toJson()).everyMonths)
    }

    @Test
    fun suggestsUntaggedPaymentsThatLookLikeAShare() {
        val other = plan.copy(members = listOf(alex, sam.copy(shareMinor = 2_500)))
        fun candidate(
            amount: Long,
            payee: String,
            notes: String = "",
        ) = PaymentCandidate("c$amount$payee", "2026-11-02", amount, payee, notes)
        val suggestions =
            SharedBills.suggestions(
                plan,
                listOf(
                    candidate(4_000, "Zelle from Alex"),
                    candidate(8_000, "ZELLE PAYMENT"),
                    candidate(4_000, "VENMO SAM"),
                    candidate(4_000, "Alex", "#att-plan #alex"),
                    candidate(3_999, "Alex"),
                    candidate(-4_000, "Alex"),
                    candidate(52_000, "Alex"),
                    candidate(4_000, "ZELLE", "from Sam"),
                ),
            )
        assertEquals(listOf(listOf(alex), listOf(alex, sam), listOf(sam), listOf(sam)), suggestions.map { it.members })
        assertEquals(listOf(sam.copy(shareMinor = 2_500)), SharedBills.suggestions(other, listOf(candidate(5_000, "Bob")))[0].members)
        assertEquals(2, SharedBills.monthsCovered(alex, 8_000))
        assertEquals(null, SharedBills.monthsCovered(alex.copy(shareMinor = 0), 8_000))
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

    @Test
    fun addsAndRemovesTagsInNotes() {
        assertEquals("#att-plan #alex", SharedBills.tagNotes("", plan, alex))
        assertEquals("Cash #att-plan #alex", SharedBills.tagNotes(" Cash ", plan, alex))
        assertEquals("Zelle #att-plan #alex", SharedBills.tagNotes("Zelle #att-plan", plan, alex))
        assertEquals("#alex #att-plan", SharedBills.tagNotes("#alex #att-plan", plan, alex))
        assertEquals("Zelle #alexander", SharedBills.untagNotes("Zelle #att-plan #alex #alexander", plan))
        assertEquals("Rent", SharedBills.untagNotes("#sam Rent #att-plan", plan))
        assertEquals("", SharedBills.untagNotes("#att-plan", plan))
    }
}
