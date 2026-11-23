package app.tandemforactual.ui.app

import app.tandemforactual.ui.core.state.MENU_PINS_UNSET

internal val defaultMenuPins =
    listOf("Add transaction", "Scan a receipt", "Bill calendar", "Spending trends", "Retirement & savings", "Budget checkup")

internal fun decodeMenuPins(raw: String): List<String> =
    if (raw == MENU_PINS_UNSET) defaultMenuPins else raw.split('\n').filter { it.isNotBlank() }

internal fun toggleMenuPin(
    raw: String,
    label: String,
): String {
    val pins = decodeMenuPins(raw)
    return (if (label in pins) pins - label else pins + label).joinToString("\n")
}

/** Pinned features first, plus anything that needs attention; everything else waits behind "All features". */
internal class MenuSections(
    val pinned: List<AppMenuItem>,
    val others: List<AppMenuItem>,
)

internal fun menuSections(
    items: List<AppMenuItem>,
    raw: String,
): MenuSections {
    val pins = decodeMenuPins(raw)
    val alerts = items.filter { it.hintIsAlert && it.label !in pins }
    val pinned = items.filter { it.label in pins }.sortedBy { pins.indexOf(it.label) }
    return MenuSections(alerts + pinned, items.filterNot { it.label in pins || it.hintIsAlert })
}

internal fun searchMenu(
    items: List<AppMenuItem>,
    query: String,
): List<AppMenuItem> {
    val words = normalize(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return items.filter { item ->
        val text = normalize("${item.label} ${item.hint.orEmpty()}")
        words.all { it in text }
    }
}

private fun normalize(text: String): String =
    text
        .lowercase()
        .replace('’', '\'')
        .replace(Regex("\\s+"), " ")
        .trim()
