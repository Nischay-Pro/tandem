package app.tandemforactual.ui

import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.templates.readablePadding
import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptiveLayoutTest {
    @Test
    fun contentStaysReadableAndCenteredOnWideWindows() {
        assertEquals(16.dp, readablePadding(412.dp, 16.dp, 720.dp))
        assertEquals(16.dp, readablePadding(740.dp, 16.dp, 720.dp))
        assertEquals(240.dp, readablePadding(1200.dp, 16.dp, 720.dp))
    }
}
