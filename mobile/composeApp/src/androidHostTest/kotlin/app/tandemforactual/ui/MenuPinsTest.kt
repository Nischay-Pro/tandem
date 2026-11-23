package app.tandemforactual.ui

import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.decodeMenuPins
import app.tandemforactual.ui.app.defaultMenuPins
import app.tandemforactual.ui.app.menuSections
import app.tandemforactual.ui.app.searchMenu
import app.tandemforactual.ui.app.toggleMenuPin
import app.tandemforactual.ui.core.state.MENU_PINS_UNSET
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import kotlin.test.Test
import kotlin.test.assertEquals

class MenuPinsTest {
    private fun item(
        label: String,
        hint: String? = null,
        alert: Boolean = false,
    ) = AppMenuItem(Lucide.Pin, label, hint, alert) {}

    private val items =
        listOf(
            item("Add transaction"),
            item("Bill calendar", "Bills and paydays by day"),
            item("Where you’ve lived", "Countries, moves and their plans"),
            item("Change history", "Undo any edit"),
            item("Sync Center", "2 to review", alert = true),
        )

    @Test
    fun startsWithTheDefaultPinsAndKeepsAnEmptyChoice() {
        assertEquals(defaultMenuPins, decodeMenuPins(MENU_PINS_UNSET))
        val none = defaultMenuPins.fold(MENU_PINS_UNSET) { raw, label -> toggleMenuPin(raw, label) }
        assertEquals(emptyList(), decodeMenuPins(none))
    }

    @Test
    fun pinnedFeaturesComeFirstInPinOrderWithAlertsAlwaysShown() {
        val raw = toggleMenuPin(toggleMenuPin("", "Change history"), "Add transaction")
        val sections = menuSections(items, raw)
        assertEquals(listOf("Sync Center", "Change history", "Add transaction"), sections.pinned.map { it.label })
        assertEquals(listOf("Bill calendar", "Where you’ve lived"), sections.others.map { it.label })
        assertEquals(listOf("Add transaction"), decodeMenuPins(toggleMenuPin(raw, "Change history")))
    }

    @Test
    fun searchMatchesEveryWordInTheNameOrDescription() {
        assertEquals(listOf("Bill calendar"), searchMenu(items, "  paydays  BILLS ").map { it.label })
        assertEquals(listOf("Where you’ve lived"), searchMenu(items, "you've").map { it.label })
        assertEquals(listOf("Change history"), searchMenu(items, "undo").map { it.label })
        assertEquals(emptyList(), searchMenu(items, "   ").map { it.label })
        assertEquals(emptyList(), searchMenu(items, "retirement").map { it.label })
    }
}
