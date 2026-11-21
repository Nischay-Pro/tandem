package app.tandemforactual.ui

import app.tandemforactual.api.client.BudgetCatalogItem
import kotlin.test.Test
import kotlin.test.assertEquals

class CachedBudgetNameTest {
    private val cached = BudgetCatalogItem(budgetId = "b1", name = "Saved budget · USD", isEncrypted = false)

    @Test
    fun keepsTheKnownNameForTheSameBudget() {
        assertEquals("Demo household", cachedBudgetName("b1", "Demo household", cached))
    }

    @Test
    fun usesTheCachedNameOtherwise() {
        assertEquals("Saved budget · USD", cachedBudgetName("b1", "", cached))
        assertEquals("Saved budget · USD", cachedBudgetName("other", "Demo household", cached))
    }
}
