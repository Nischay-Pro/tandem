package app.tandemforactual.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosSyncEventSourceTest {
    @Test
    fun resumeSignalRequiresAnInactiveToActiveTransitionAndStopsAfterClose() {
        val source = IosSyncEventSource()
        val resumeEvents = mutableListOf<Unit>()
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val collector =
            scope.launch(EmptyCoroutineContext) {
                source.appResumeEvents.collect { resumeEvents += it }
            }

        try {
            post(requireNotNull(UIApplicationDidBecomeActiveNotification))
            assertTrue(resumeEvents.isEmpty(), "An already-active app should not emit a resume")

            post(requireNotNull(UIApplicationWillResignActiveNotification))
            post(requireNotNull(UIApplicationDidBecomeActiveNotification))
            assertEquals(1, resumeEvents.size)

            source.close()
            post(requireNotNull(UIApplicationWillResignActiveNotification))
            post(requireNotNull(UIApplicationDidBecomeActiveNotification))
            assertEquals(1, resumeEvents.size, "Closed sources must release their observers")
        } finally {
            collector.cancel()
            scope.cancel()
            source.close()
        }
    }

    private fun post(name: String) {
        NSNotificationCenter.defaultCenter.postNotificationName(name, `object` = null)
    }
}
