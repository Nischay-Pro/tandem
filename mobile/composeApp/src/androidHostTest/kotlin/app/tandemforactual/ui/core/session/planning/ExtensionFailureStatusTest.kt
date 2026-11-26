package app.tandemforactual.ui.core.session.planning

import app.tandemforactual.ui.core.state.PlanningSyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionFailureStatusTest {
    @Test
    fun eachFailureGetsItsOwnStatus() {
        assertEquals(PlanningSyncStatus.SIGN_IN_EXPIRED, extensionFailureStatus(401))
        assertEquals(PlanningSyncStatus.NO_ACCESS, extensionFailureStatus(403))
        assertEquals(PlanningSyncStatus.BUSY, extensionFailureStatus(429))
        assertEquals(PlanningSyncStatus.WAITING, extensionFailureStatus(503))
        assertEquals(PlanningSyncStatus.WAITING, extensionFailureStatus(null))
    }

    @Test
    fun onlyProblemsThatMayClearAreRetried() {
        assertEquals(
            setOf(PlanningSyncStatus.WAITING, PlanningSyncStatus.SIGN_IN_EXPIRED, PlanningSyncStatus.NO_ACCESS, PlanningSyncStatus.BUSY),
            PlanningSyncStatus.entries.filter { it.retryable }.toSet(),
        )
        assertFalse(PlanningSyncStatus.NEEDS_RECOVERY_KEY.retryable)
        assertTrue(PlanningSyncStatus.WAITING.retryable)
    }
}
