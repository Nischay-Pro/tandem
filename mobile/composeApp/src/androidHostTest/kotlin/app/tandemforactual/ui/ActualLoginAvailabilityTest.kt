package app.tandemforactual.ui

import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActualLoginAvailabilityTest {
    @Test
    fun actualLoginAvailabilityDistinguishesTheFourConnectionOutcomes() =
        runBlocking {
            assertLoginMethodsOutcomes(loginMethodsOutcomes())
        }
}
