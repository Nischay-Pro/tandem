package app.tandemforactual.ui

import app.tandemforactual.ui.core.state.writeStatusLabel
import kotlin.test.assertEquals
import org.junit.Test

class SyncStatusLabelTest {
    @Test
    fun editStatesUseTheSameNamesAcrossTheSyncCenterAndReviewScreens() {
        assertEquals("Needs review", writeStatusLabel("conflict"))
        assertEquals("Status unknown", writeStatusLabel("uncertain"))
        assertEquals("Rejected", writeStatusLabel("rejected"))
    }
}
