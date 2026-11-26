package app.tandemforactual.android

import android.content.Intent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TandemProcessTest {
    @Test
    fun crashHandlerWrapsThePreviousHandlerOncePerProcess() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        val forwarded = mutableListOf<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> forwarded += error }
        try {
            val recorded = mutableListOf<String>()
            assertTrue(TandemProcess.installCrashHandler { thread, _ -> recorded += thread })
            assertFalse(TandemProcess.installCrashHandler { thread, _ -> recorded += "again-$thread" })
            val error = IllegalStateException("boom")
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)
            assertEquals(listOf(Thread.currentThread().name), recorded)
            assertEquals(listOf<Throwable>(error), forwarded)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    @Test
    fun launchIntentIsHandledOnlyOnAFreshStart() {
        assertTrue(isFreshLaunchIntent(restored = false, flags = 0))
        assertFalse(isFreshLaunchIntent(restored = true, flags = 0))
        assertFalse(isFreshLaunchIntent(restored = false, flags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        assertTrue(isFreshLaunchIntent(restored = false, flags = Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test
    fun themeLocaleAndFontChangesDoNotRecreateTheActivity() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val handled =
            Regex("""android:configChanges="([^"]+)"""")
                .find(manifest)!!
                .groupValues[1]
                .split("|")
        assertTrue(handled.containsAll(listOf("uiMode", "locale", "layoutDirection", "fontScale", "orientation", "screenSize")))
    }
}
