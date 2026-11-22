package app.tandemforactual.data.regions

/** Where a provider or rule set applies. [GLOBAL] is for features that work the same everywhere. */
enum class Region(
    val displayName: String,
) {
    GLOBAL("Everywhere"),
    US("United States"),
    CA("Canada"),
    EU("European Union"),
    UK("United Kingdom"),
    BR("Brazil"),
    NZ("New Zealand"),
    IN("India"),
}
