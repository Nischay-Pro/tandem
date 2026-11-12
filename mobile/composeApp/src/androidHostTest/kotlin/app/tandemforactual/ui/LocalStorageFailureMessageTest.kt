package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalStorageFailureMessageTest {
    @Test
    fun reportsStorageFullWithARecoveryStep() {
        assertEquals(
            "This device is out of storage. Free up space and try again.",
            localStorageFailureMessage(
                IllegalStateException("SQLITE_FULL: database or disk is full"),
                "Couldn’t save this change.",
            ),
        )
    }

    @Test
    fun recognizesNoSpaceInWrappedStorageErrors() {
        val error = IllegalStateException("Local database write failed", java.io.IOException("No space left on device"))

        assertEquals(
            "This device is out of storage. Free up space and try again.",
            localStorageFailureMessage(error, "Couldn’t save this change."),
        )
    }

    @Test
    fun leavesOtherWriteErrorsSpecificToTheirOperation() {
        assertEquals(
            "Tandem couldn’t save your edit. Check the budget and try again.",
            localStorageFailureMessage(
                IllegalStateException("disk I/O error"),
                "Tandem couldn’t save your edit. Check the budget and try again.",
            ),
        )
    }
}
