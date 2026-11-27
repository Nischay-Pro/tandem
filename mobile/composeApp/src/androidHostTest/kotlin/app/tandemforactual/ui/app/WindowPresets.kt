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

    /** A third of a tablet in split screen, beside a banking app. */
    const val TABLET_THIRD = "w320dp-h800dp-xhdpi"

    /** A third of a taller tablet in split screen. */
    const val TALL_TABLET_THIRD = "w360dp-h1000dp-xhdpi"

    /** Half of an unfolded foldable in split screen. */
    const val FOLD_HALF = "w420dp-h880dp-xxhdpi"

    /** A small floating window over another app. */
    const val FLOATING = "w340dp-h560dp-xxhdpi"

    /** The narrowest window a split or pop-up can shrink to. */
    const val VERY_NARROW = "w280dp-h640dp-xxhdpi"
}
