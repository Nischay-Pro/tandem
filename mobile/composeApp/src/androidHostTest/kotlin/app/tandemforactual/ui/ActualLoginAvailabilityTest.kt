package app.tandemforactual.ui

import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActualLoginAvailabilityTest {
    @Test
    fun actualLoginAvailabilityDistinguishesTheFourConnectionOutcomes() =
        runBlocking {
            assertLoginMethodsOutcomes(loginMethodsOutcomes())
        }
}
