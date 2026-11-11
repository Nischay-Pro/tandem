package app.tandemforactual.ui

import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActualLoginAvailabilityTest {
    @Test
    fun actualLoginAvailabilityDistinguishesTheFourConnectionOutcomes() =
        runBlocking {
            assertLoginMethodsOutcomes(loginMethodsOutcomes())
        }
}
