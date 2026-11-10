package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionDateValidationTest {
    @Test
    fun enforcesTheMinimumAndMaximumTransactionDates() {
        assertEquals(
            TransactionDateValidationStatus.BEFORE_MINIMUM,
            validateTransactionDate("1994-12-31", "2026-11-10").status,
        )
        assertEquals(
            TransactionDateValidationStatus.VALID,
            validateTransactionDate("1995-01-01", "2026-11-10").status,
        )
        assertEquals(
            TransactionDateValidationStatus.CONFIRMATION_REQUIRED,
            validateTransactionDate("2031-11-10", "2026-11-10").status,
        )
        assertEquals(
            TransactionDateValidationStatus.VALID,
            validateTransactionDate("2031-11-10", "2026-11-10", confirmedFutureDate = "2031-11-10").status,
        )
        val tooFarAhead = validateTransactionDate("2031-11-11", "2026-11-10")
        assertEquals(TransactionDateValidationStatus.AFTER_MAXIMUM, tooFarAhead.status)
        assertEquals("2031-11-10", tooFarAhead.maximumDate)
        assertEquals(
            TransactionDateValidationStatus.AFTER_MAXIMUM,
            validateTransactionDate("2062-03-04", "2026-11-10").status,
        )
        assertEquals(
            "Choose a date within five years of today. Later dates are blocked to catch year typos.",
            transactionDateValidationMessage(tooFarAhead),
        )
    }

    @Test
    fun asksForConfirmationOnlyWhenTheDateIsMoreThanOneYearAhead() {
        assertEquals(
            TransactionDateValidationStatus.VALID,
            validateTransactionDate("2027-11-10", "2026-11-10").status,
        )
        val futureDate = "2027-11-11"
        assertEquals(
            TransactionDateValidationStatus.CONFIRMATION_REQUIRED,
            validateTransactionDate(futureDate, "2026-11-10").status,
        )
        assertEquals(
            TransactionDateValidationStatus.VALID,
            validateTransactionDate(futureDate, "2026-11-10", confirmedFutureDate = futureDate).status,
        )
        assertEquals(
            TransactionDateValidationStatus.CONFIRMATION_REQUIRED,
            validateTransactionDate(futureDate, "2026-11-10", confirmedFutureDate = "2027-11-12").status,
        )
    }

    @Test
    fun anniversaryBoundariesClampToTheLastDayOfFebruaryInNonLeapYears() {
        assertEquals(
            TransactionDateValidationStatus.VALID,
            validateTransactionDate("2025-02-28", "2024-02-29").status,
        )
        assertEquals(
            TransactionDateValidationStatus.CONFIRMATION_REQUIRED,
            validateTransactionDate("2025-03-01", "2024-02-29").status,
        )
        assertEquals(
            "2029-02-28",
            validateTransactionDate("2029-02-28", "2024-02-29").maximumDate,
        )
        assertEquals(
            TransactionDateValidationStatus.AFTER_MAXIMUM,
            validateTransactionDate("2029-03-01", "2024-02-29").status,
        )
    }

    @Test
    fun rejectsMalformedAndImpossibleDates() {
        for (value in listOf("2026-2-01", "2026-02-30", "0000-01-01", "not-a-date")) {
            assertEquals(
                TransactionDateValidationStatus.INVALID,
                validateTransactionDate(value, "2026-11-10").status,
                value,
            )
        }
    }
}
