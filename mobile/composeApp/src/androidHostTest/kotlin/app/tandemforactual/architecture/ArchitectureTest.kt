package app.tandemforactual.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Keeps the feature/core/design structure from eroding. Existing violations live in
 * `architecture-baseline.txt` and may only be removed, never added.
 */
class ArchitectureTest {
    private val root = "app.tandemforactual.ui"
    private val files =
        Konsist
            .scopeFromProduction("composeApp")
            .files
            .filter { it.packagee?.name?.startsWith(root) == true }

    private val baselineFile = File("src/androidHostTest/resources/architecture-baseline.txt")

    private val baseline: Set<String> =
        baselineFile
            .takeIf { it.exists() }
            ?.readLines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
            ?.toSet()
            .orEmpty()

    private fun KoFileDeclaration.pkg() = packagee?.name.orEmpty()

    private fun KoFileDeclaration.feature() =
        pkg().removePrefix("$root.feature.").substringBefore('.').takeIf {
            pkg().startsWith("$root.feature.")
        }

    private fun check(
        rule: String,
        violations: List<String>,
    ) {
        if (System.getenv("WRITE_ARCH_BASELINE") == "1") {
            baselineFile.parentFile.mkdirs()
            baselineFile.appendText(violations.sorted().joinToString("") { "$rule $it\n" })
            return
        }
        val current = violations.map { "$rule $it" }.toSet()
        val fresh = current.filterNot { it in baseline }
        if (fresh.isNotEmpty()) fail("New $rule violations:\n" + fresh.joinToString("\n"))
        val fixed = baseline.filter { it.startsWith("$rule ") && it !in current }
        if (fixed.isNotEmpty()) fail("Fixed $rule violations; remove them from architecture-baseline.txt:\n" + fixed.joinToString("\n"))
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
    fun filesStaySmall() {
        check("size", files.filter { it.text.lines().size > MAX_FILE_LINES }.map { it.name })
    }

    @Test
    fun functionsStaySmall() {
        check(
            "function-size",
            files
                .flatMap { f ->
                    f
                        .functions(
                            includeNested = true,
                            includeLocal = false,
                        ).filter { it.text.lines().size > MAX_FUNCTION_LINES }
                        .map { "${f.name}.${it.name}" }
                }.distinct(),
        )
    }

    private companion object {
        const val MAX_FILE_LINES = 400
        const val MAX_FUNCTION_LINES = 80
    }
}
