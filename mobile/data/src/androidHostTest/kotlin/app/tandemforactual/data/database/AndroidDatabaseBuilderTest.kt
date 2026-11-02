package app.tandemforactual.data.database

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidDatabaseBuilderTest {
    @Test
    fun builderUsesTheApplicationContextAndCreatesARoomBuilder() {
        assertNotNull(tandemDatabaseBuilder(RuntimeEnvironment.getApplication()))
    }
}
