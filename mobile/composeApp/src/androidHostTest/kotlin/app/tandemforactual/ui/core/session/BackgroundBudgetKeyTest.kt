package app.tandemforactual.ui.core.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
