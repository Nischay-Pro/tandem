package app.tandemforactual.ui.app

/** Robolectric qualifiers for the window sizes Tandem has to work at, one per width class plus short and folded windows. */
internal object WindowPresets {
    const val PHONE = "w411dp-h891dp-xxhdpi"
    const val MEDIUM = "w600dp-h960dp-xhdpi"
    const val EXPANDED = "w840dp-h1200dp-xhdpi"
    const val LARGE = "w1200dp-h800dp-xhdpi"
    const val SHORT_PHONE = "w411dp-h400dp-xxhdpi"
    const val SHORT_WIDE = "w840dp-h400dp-xxhdpi"
    const val BOOK_FOLD = "w841dp-h701dp-xhdpi"
}
