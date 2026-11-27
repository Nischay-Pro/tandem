package app.tandemforactual.ui.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileDropTest {
    @Test
    fun photosAndStatementsAreToldApartByTypeOrName() {
        assertEquals(DroppedFileKind.IMAGE, droppedFileKind("image/jpeg", "receipt.jpg"))
        assertEquals(DroppedFileKind.STATEMENT, droppedFileKind("text/csv", "march.csv"))
        assertEquals(DroppedFileKind.STATEMENT, droppedFileKind("application/octet-stream", "Checking.QFX"))
        assertEquals(DroppedFileKind.STATEMENT, droppedFileKind("text/plain", "export.qif"))
        assertEquals(DroppedFileKind.STATEMENT, droppedFileKind("application/x-ofx; charset=utf-8", null))
        assertNull(droppedFileKind("application/pdf", "statement.pdf"))
        assertNull(droppedFileKind("text/plain", "notes.txt"))
        assertNull(droppedFileKind(null, null))
    }

    @Test
    fun aDragIsOnlyAcceptedForTheKindsTheTargetTakes() {
        assertTrue(mayAcceptDrop("image/png", statements = false, images = true))
        assertFalse(mayAcceptDrop("image/png", statements = true, images = false))
        assertTrue(mayAcceptDrop("application/octet-stream", statements = true, images = false))
        assertTrue(mayAcceptDrop("text/csv", statements = true, images = true))
        assertFalse(mayAcceptDrop("application/pdf", statements = true, images = true))
    }
}
