package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionDateValidationTest {
    @Test
    fun enforcesTheActualMinimumAndAcceptsFarFutureDatesAfterConfirmation() {
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
            validateTransactionDate("2062-03-04", "2026-11-10").status,
        )
        assertEquals(
            TransactionDateValidationStatus.VALID,
            validateTransactionDate(
                "2062-03-04",
                "2026-11-10",
                confirmedFutureDate = "2062-03-04",
            ).status,
        )
        assertEquals(
            "Choose a date on or after ${displayTransactionDate("1995-01-01")}.",
            transactionDateValidationMessage(
                validateTransactionDate("1994-12-31", "2026-11-10"),
            ),
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
            validateTransactionDate(
                futureDate,
                "2026-11-10",
                confirmedFutureDate = futureDate,
            ).status,
        )
        assertEquals(
            TransactionDateValidationStatus.CONFIRMATION_REQUIRED,
            validateTransactionDate(
                futureDate,
                "2026-11-10",
                confirmedFutureDate = "2027-11-12",
            ).status,
        )
    }

    @Test
    fun anniversaryConfirmationBoundaryClampsToTheLastDayOfFebruary() {
        assertEquals(
            TransactionDateValidationStatus.VALID,
            validateTransactionDate("2025-02-28", "2024-02-29").status,
        )
        assertEquals(
            TransactionDateValidationStatus.CONFIRMATION_REQUIRED,
            validateTransactionDate("2025-03-01", "2024-02-29").status,
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
