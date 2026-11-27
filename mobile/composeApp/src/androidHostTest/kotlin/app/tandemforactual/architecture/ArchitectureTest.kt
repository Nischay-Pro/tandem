package app.tandemforactual.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import kotlin.test.Test
import kotlin.test.fail

/** Keeps the feature/core/design structure from eroding. Any violation fails the build; there is no allowlist. */
class ArchitectureTest {
    private val root = "app.tandemforactual.ui"
    private val files =
        Konsist
            .scopeFromProduction("composeApp")
            .files
            .filter { it.packagee?.name?.startsWith(root) == true }

    private val allModules =
        listOf("composeApp", "data", "api-client", "androidApp", "watch-protocol", "wearable-play", "wearApp").flatMap { module ->
            Konsist.scopeFromProduction(module).files.map { module to it }
        }

    private fun sizeKey(
        module: String,
        name: String,
    ) = if (module == "composeApp") name else "$module:$name"

    private fun KoFileDeclaration.pkg() = packagee?.name.orEmpty()

    private fun KoFileDeclaration.feature() =
        pkg().removePrefix("$root.feature.").substringBefore('.').takeIf {
            pkg().startsWith("$root.feature.")
        }

    private fun check(
        rule: String,
        violations: List<String>,
    ) {
        if (violations.isNotEmpty()) fail("$rule violations:\n" + violations.sorted().joinToString("\n"))
    }

    @Test
    fun everyFileLivesInAnAllowedArea() {
        val allowed = listOf("app", "core", "design", "feature").map { "$root.$it" }
        check(
            "area",
            files
                .filter { f ->
                    allowed.none { f.pkg() == it || f.pkg().startsWith("$it.") }
                }.map { it.path.substringAfter("kotlin/") },
        )
    }

    @Test
    fun packageMatchesFolder() {
        check(
            "package",
            files
                .filterNot {
                    it.path
                        .substringBeforeLast('/')
                        .replace('/', '.')
                        .endsWith(it.pkg())
                }.map { it.path.substringAfter("kotlin/") },
        )
    }

    @Test
    fun featuresDoNotImportOtherFeatures() {
        check(
            "cross-feature",
            files
                .mapNotNull { f -> f.feature()?.let { own -> f to own } }
                .flatMap { (f, own) ->
                    f.imports
                        .map { it.name }
                        .filter { it.startsWith("$root.feature.") && !it.startsWith("$root.feature.$own.") }
                        .map { "${f.name} -> ${it.removePrefix("$root.feature.").substringBefore('.')}" }
                }.distinct(),
        )
    }

    @Test
    fun coreAndDesignDoNotDependOnFeaturesOrTheShell() {
        check(
            "layering",
            files
                .filter { it.pkg().startsWith("$root.core") || it.pkg().startsWith("$root.design") }
                .flatMap { f ->
                    f.imports
                        .map { it.name }
                        .filter { it.startsWith("$root.feature.") || it.startsWith("$root.app.") }
                        .map { "${f.name} -> ${it.removePrefix("$root.").substringBeforeLast('.')}" }
                }.distinct(),
        )
    }

    @Test
    fun featuresDoNotWriteSessionState() {
        val write = Regex("""\bmobileSession(?:\.\w+)+\s*(?:=(?!=)|\+=|-=)""")
        check(
            "session-write",
            files
                .filter { it.feature() != null }
                .flatMap { f -> write.findAll(f.text).map { "${f.name} ${it.value.substringBefore('=').trim().trimEnd('+', '-').trim()}" } }
                .distinct(),
        )
    }

    /** Google Play services stays in the module a store build can drop, and in the watch app's Data Layer package. */
    @Test
    fun playServicesStayIsolated() {
        check(
            "play-services",
            allModules
                .filterNot { (module, file) ->
                    module == "wearable-play" || (module == "wearApp" && file.pkg().startsWith("app.tandemforactual.wear.link"))
                }.flatMap { (module, file) ->
                    file.imports
                        .map { it.name }
                        .filter { it.startsWith("com.google.android.gms.") }
                        .map { "$module:${file.name} -> $it" }
                }.distinct(),
        )
    }

    /** The watch app shows what the phone sends; it never reaches the budget, its database or the server. */
    @Test
    fun theWatchAppOnlyKnowsTheSummary() {
        check(
            "watch-scope",
            allModules
                .filter { (module, _) -> module == "wearApp" || module == "watch-protocol" }
                .flatMap { (module, file) ->
                    file.imports
                        .map { it.name }
                        .filter { it.startsWith("app.tandemforactual.") }
                        .filterNot { it.startsWith("app.tandemforactual.wear.") || it.startsWith("app.tandemforactual.watch.") }
                        .map { "$module:${file.name} -> $it" }
                }.distinct(),
        )
    }

    @Test
    fun filesStaySmall() {
        check(
            "size",
            allModules.filter { (_, f) -> codeLines(f.text, countHeader = false) > MAX_FILE_LINES }.map { (m, f) -> sizeKey(m, f.name) },
        )
    }

    @Test
    fun functionsStaySmall() {
        check(
            "function-size",
            allModules
                .flatMap { (m, f) ->
                    f
                        .functions(
                            includeNested = true,
                            includeLocal = false,
                        ).filter { codeLines(it.text, countHeader = true) > MAX_FUNCTION_LINES }
                        .map { "${sizeKey(m, f.name)}.${it.name}" }
                }.distinct(),
        )
    }

    @Test
    fun budgetMathsOracleStaysIndependentOfTheEngine() {
        val oracle = "app.tandemforactual.data.oracle"
        val oracleFiles = Konsist.scopeFromModule("data").files.filter { it.pkg() == oracle || it.pkg().startsWith("$oracle.") }
        if (oracleFiles.isEmpty()) fail("No files found in $oracle")
        val app = Regex("""\bapp\.tandemforactual\.[\w.]+""")
        check(
            "oracle-independence",
            oracleFiles.flatMap { f ->
                app
                    .findAll(f.text)
                    .map { it.value }
                    .filterNot { it == oracle || it.startsWith("$oracle.") }
                    .map { "${f.name} -> $it" }
            } +
                allModules.filter { (_, f) -> f.text.contains(oracle) }.map { (m, f) -> "$m:${f.name} -> $oracle" },
        )
    }

    /** Lines that hold code: blank lines, comments, and a file's package and imports don't count. */
    private fun codeLines(
        text: String,
        countHeader: Boolean,
    ): Int =
        text.lines().count { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() &&
                !trimmed.startsWith("//") &&
                !trimmed.startsWith("/*") &&
                !trimmed.startsWith("*") &&
                (countHeader || !(trimmed.startsWith("import ") || trimmed.startsWith("package ")))
        }

    private companion object {
        const val MAX_FILE_LINES = 600
        const val MAX_FUNCTION_LINES = 80
    }
}
