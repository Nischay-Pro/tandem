package app.tandemforactual.ui.app

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListDetailPanesTest {
    @Test
    fun panesSitSideBySideOnlyWhenBothFit() {
        assertFalse(usesListDetailPanes(411f))
        assertFalse(usesListDetailPanes(699f))
        assertTrue(usesListDetailPanes(700f))
        assertTrue(usesListDetailPanes(761f))
        assertEquals(360.dp, listPaneWidth(761f))
        assertEquals(420.dp, listPaneWidth(1_200f))
    }
}
