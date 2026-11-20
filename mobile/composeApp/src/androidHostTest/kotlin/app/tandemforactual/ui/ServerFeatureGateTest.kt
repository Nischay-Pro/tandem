package app.tandemforactual.ui

import app.tandemforactual.api.client.ServerCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerFeatureGateTest {
    private val current = ServerCapabilities(1, 1, setOf("hold-total", "budget-order-merge"), "26.11.0")

    @Test
    fun changesWaitOnlyForFeaturesTheServerLacks() {
        assertNull(missingServerFeature(BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH, current))
        assertNull(missingServerFeature(BudgetReorderKind.SET_ORDER, current))
        assertEquals("hold-total", missingServerFeature(BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH, ServerCapabilities.LEGACY))
        assertEquals("budget-order-merge", missingServerFeature(BudgetReorderKind.SET_ORDER, ServerCapabilities.LEGACY))
    }

    @Test
    fun unknownCapabilitiesAndUngatedChangesNeverWait() {
        assertNull(missingServerFeature(BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH, null))
        assertNull(missingServerFeature(BudgetReorderKind.RESET_BUDGET_HOLD, ServerCapabilities.LEGACY))
    }
}
