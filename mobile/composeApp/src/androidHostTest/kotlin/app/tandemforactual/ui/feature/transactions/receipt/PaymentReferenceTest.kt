package app.tandemforactual.ui.feature.transactions.receipt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaymentReferenceTest {
    private val known =
        listOf(
            KnownPayment("2026-11-27", "Lunch", null),
            KnownPayment("2026-11-28", "Ref 433212345678 · Sent Rs.250.00", null),
            KnownPayment("2026-11-20", null, "433200001111"),
        )

    @Test
    fun aPaymentIsFoundByTheReferenceInItsNotesOrImportId() {
        assertEquals("2026-11-28", findByReference("433212345678", known)?.date)
        assertEquals("2026-11-20", findByReference(" 433200001111 ", known)?.date)
        assertNull(findByReference("999999999999", known))
        assertNull(findByReference("123", known))
        assertNull(findByReference(null, known))
    }

    @Test
    fun theReferenceIsKeptAtTheFrontOfTheNotes() {
        assertEquals("Ref 4332", notesWithReference("", "4332"))
        assertEquals("Ref 4332 · Sent to SWIGGY", notesWithReference("Sent to SWIGGY", "4332"))
        assertEquals("UPI 4332 paid", notesWithReference("UPI 4332 paid", "4332"))
        assertEquals(200, notesWithReference("x".repeat(300), "4332").length)
    }
}
