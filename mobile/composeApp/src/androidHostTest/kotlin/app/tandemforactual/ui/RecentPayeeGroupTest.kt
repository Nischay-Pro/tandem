package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentPayeeGroupTest {
    private val payees = (1..12).map { SelectorItem("p$it", "Payee $it") }

    @Test
    fun recentPayeesComeFirstInUseOrder() {
        val groups = recentPayeeGroup(payees, listOf("p7", "gone", "p2"))
        assertEquals(listOf("Recent"), groups.map { it.title })
        assertEquals(listOf("p7", "p2"), groups.single().items.map { it.id })
    }

    @Test
    fun shortListsAndNoHistoryStayFlat() {
        assertEquals(emptyList(), recentPayeeGroup(payees.take(8), listOf("p1")))
        assertEquals(emptyList(), recentPayeeGroup(payees, emptyList()))
    }
}
