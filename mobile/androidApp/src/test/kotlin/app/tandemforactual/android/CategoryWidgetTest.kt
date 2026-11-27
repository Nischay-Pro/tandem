package app.tandemforactual.android

import app.tandemforactual.data.store.WidgetAccount
import app.tandemforactual.data.store.WidgetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryWidgetTest {
    @Test
    fun widgetSizeSetsHowManyCategoriesFit() {
        assertEquals(CategoryWidgetSize.COMPACT, CategoryWidgetSize.of(110f, 110f))
        assertEquals(CategoryWidgetSize.COMPACT, CategoryWidgetSize.of(80f, 60f))
        assertEquals(CategoryWidgetSize.COMPACT, CategoryWidgetSize.of(179f, 300f))
        assertEquals(CategoryWidgetSize.SHORT, CategoryWidgetSize.of(250f, 179f))
        assertEquals(CategoryWidgetSize.TALL, CategoryWidgetSize.of(250f, 180f))
        assertEquals(CategoryWidgetSize.TALLER, CategoryWidgetSize.of(180f, 250f))
        assertEquals(listOf(1, 2, 4, 6), CategoryWidgetSize.entries.map { it.rows })
    }

    @Test
    fun categorySetupListsCategoriesUnderTheirGroups() {
        val categories = listOf(WidgetCategory("food", "Food", "Living", 100), WidgetCategory("misc", "Misc", "", -5))
        val content = categoryContent(categories, listOf("misc", "gone"))

        assertEquals(listOf("Living", "Category"), content.options!!.map { it.subtitle })
        assertEquals(setOf("misc", "gone"), content.initial)
        assertTrue(content.multiple)
        assertTrue(categoryContent(null, emptyList()).emptyMessage.contains("app lock"))
        assertTrue(categoryContent(emptyList(), emptyList()).emptyMessage.contains("no categories"))
    }

    @Test
    fun quickAddStartsInTheLastUsedAccountUnlessOneIsPicked() {
        val accounts = listOf(WidgetAccount("checking", "Checking"))

        assertEquals(listOf("", "checking"), quickAddContent(accounts, null).options!!.map { it.id })
        assertEquals(setOf("checking"), quickAddContent(accounts, "checking").initial)
        assertEquals(setOf(""), quickAddContent(accounts, "closed").initial)
        assertEquals(setOf(""), quickAddContent(emptyList(), null).initial)
    }
}
