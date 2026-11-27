package app.tandemforactual.ui.feature.settings

import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.automation_detail
import app.tandemforactual.ui.generated.resources.automation_detail_assistants
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AutomationSectionTest {
    @Test
    @Config(sdk = [35])
    fun assistantsAreNotMentionedBeforeAndroid16() {
        assertEquals(Res.string.automation_detail, automationDetail())
    }

    @Test
    @Config(sdk = [37])
    fun assistantsAreMentionedFromAndroid16() {
        assertEquals(Res.string.automation_detail_assistants, automationDetail())
    }
}
