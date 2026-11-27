package app.tandemforactual.ui.core.session

import app.tandemforactual.ui.app.shell.keptPasswordChanged
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackgroundBudgetKeyTest {
    private class MemoryStore(
        var value: String? = null,
    ) : DeviceCredentialStore {
        override fun read() = value

        override fun write(value: String) {
            this.value = value
        }

        override fun clear() {
            value = null
        }
    }

    @Test
    fun keepsTheBudgetWithItsPassword() {
        assertEquals("b1" to "pass\nword", BackgroundBudgetKey.decode(BackgroundBudgetKey.encode("b1", "pass\nword")))
        assertNull(BackgroundBudgetKey.decode("b1"))
        assertNull(BackgroundBudgetKey.decode("\npw"))
        assertNull(BackgroundBudgetKey.decode(null))
        val store = MemoryStore(BackgroundBudgetKey.encode("b1", "pw"))
        assertEquals("b1", BackgroundBudgetKey.budgetIn(store))
        store.clear()
        assertNull(BackgroundBudgetKey.budgetIn(store))
        assertNull(
            BackgroundBudgetKey.budgetIn(
                object : DeviceCredentialStore {
                    override fun read(): String = error("locked")

                    override fun write(value: String) = Unit

                    override fun clear() = Unit
                },
            ),
        )
    }

    @Test
    fun aPasswordChangedInActualForgetsTheKeptOne() {
        val store = MemoryStore(BackgroundBudgetKey.encode("b1", "old"))
        assertFalse(keptPasswordChanged(null, "b1", "new"))
        assertFalse(keptPasswordChanged(store, "b1", ""))
        assertFalse(keptPasswordChanged(store, "b1", "old"))
        assertFalse(keptPasswordChanged(store, "b2", "new"))
        assertEquals("b1", BackgroundBudgetKey.budgetIn(store))
        assertTrue(keptPasswordChanged(store, "b1", "new"))
        assertNull(store.value)
        assertFalse(keptPasswordChanged(store, "b1", "new"))
    }
}
