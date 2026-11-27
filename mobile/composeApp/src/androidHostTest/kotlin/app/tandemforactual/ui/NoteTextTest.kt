package app.tandemforactual.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.LinkAnnotation
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.ui.noteWithPendingEdit
import app.tandemforactual.ui.design.rows.NoteText
import app.tandemforactual.ui.design.rows.linkedNote
import app.tandemforactual.ui.design.rows.noteLinks
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteTextTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()

        override fun openUri(uri: String) {
            opened += uri
        }
    }

    @Test
    fun findsWebAddressesWithoutTrailingPunctuation() {
        val text = "Portal: https://bank.example/login. Wiki www.example.com/a_(b), see [docs](HTTP://x.io/path)."
        val links = noteLinks(text)
        assertEquals(
            listOf("https://bank.example/login", "https://www.example.com/a_(b)", "HTTP://x.io/path"),
            links.map { it.url },
        )
        assertEquals("https://bank.example/login", text.substring(links[0].range))
        assertEquals("www.example.com/a_(b)", text.substring(links[1].range))
    }

    @Test
    fun plainTextHasNoLinks() {
        assertTrue(noteLinks("Pay rent by the 1st. Ask sam@example.com, or check www. and https:// later").isEmpty())
        assertTrue(noteLinks("").isEmpty())
    }

    @Test
    fun linksOpenThroughTheUriHandler() {
        val handler = RecordingUriHandler()
        val text = linkedNote("Bills: www.power.example and https://water.example/pay", Color.Blue, handler)
        val links = text.getLinkAnnotations(0, text.length)
        assertEquals(2, links.size)
        links.forEach { range -> range.item.linkInteractionListener?.onClick(range.item) }
        assertEquals(listOf("https://www.power.example", "https://water.example/pay"), handler.opened)
        assertEquals(listOf("https://www.power.example", "https://water.example/pay"), links.map { (it.item as LinkAnnotation.Url).url })
    }

    @Test
    fun longNotesFoldBehindShowMore() {
        val handler = RecordingUriHandler()
        val note = (1..12).joinToString("\n") { "Line $it" }
        composeTestRule.setContent {
            TandemTheme(ThemeMode.LIGHT, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                CompositionLocalProvider(LocalUriHandler provides handler) { NoteText(note) }
            }
        }
        composeTestRule.onNodeWithText("Show more").performClick()
        composeTestRule.onNodeWithText("Show less").performClick()
        composeTestRule.onNodeWithText("Show more").assertExists()
    }

    @Test
    fun shortNotesHaveNoToggle() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                NoteText("Joint account with Sam")
            }
        }
        composeTestRule.onNodeWithText("Joint account with Sam").assertExists()
        composeTestRule.onNodeWithText("Show more").assertDoesNotExist()
    }

    @Test
    fun aQueuedEditShowsInPlaceOfTheSyncedNote() {
        val record =
            BudgetRecordEntity(
                budgetId = "budget-1",
                recordType = "account",
                recordId = "account-1",
                remoteJson = """{"id":"account-1","note":"Old note"}""",
                remoteRevision = null,
                localJson = null,
                localDeleted = false,
                remoteDeleted = false,
                syncGeneration = "1",
                recordDate = null,
                accountId = null,
                categoryId = null,
                payeeId = null,
                amountMinor = null,
                budgetMonth = null,
            )
        assertEquals("Old note", noteWithPendingEdit(emptyMap(), record, "account-1"))
        assertEquals("New note", noteWithPendingEdit(mapOf("account-1" to "New note"), record, "account-1"))
        assertEquals("", noteWithPendingEdit(mapOf("account-1" to ""), record, "account-1"))
        assertEquals("", noteWithPendingEdit(emptyMap(), null, "account-1"))
    }
}
