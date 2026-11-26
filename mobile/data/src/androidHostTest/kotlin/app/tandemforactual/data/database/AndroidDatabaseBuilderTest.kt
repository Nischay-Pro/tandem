package app.tandemforactual.data.database

import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidDatabaseBuilderTest {
    @Test
    fun builderUsesTheApplicationContextAndCreatesARoomBuilder() {
        assertNotNull(tandemDatabaseBuilder(RuntimeEnvironment.getApplication()))
    }
}
