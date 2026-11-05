package app.tandemforactual.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSignalStateTest {
  @Test
  fun firstAvailableNetworkDoesNotCountAsRestoration() {
    val signals = SyncSignalState<String>()

    signals.onNetworkStarted("wifi")

    assertFalse(signals.onNetworkAvailable("wifi"))
  }

  @Test
  fun availableNetworkAfterCurrentNetworkIsLostTriggersOnce() {
    val signals = SyncSignalState<String>()
    signals.onNetworkStarted("wifi")
    signals.onNetworkLost("wifi")

    assertTrue(signals.onNetworkAvailable("wifi"))
    assertFalse(signals.onNetworkAvailable("wifi"))
  }

  @Test
  fun losingAStaleNetworkDoesNotTriggerRecovery() {
    val signals = SyncSignalState<String>()
    signals.onNetworkStarted("wifi")
    assertFalse(signals.onNetworkAvailable("cellular"))
    signals.onNetworkLost("wifi")

    assertFalse(signals.onNetworkAvailable("cellular"))
    signals.onNetworkLost("cellular")
    assertTrue(signals.onNetworkAvailable("wifi"))
  }

  @Test
  fun activityResumeSignalSkipsInitialResumeAndEmitsOnReturns() {
    val signals = SyncSignalState<String>()

    assertFalse(signals.onActivityResumed())
    assertTrue(signals.onActivityResumed())
    assertTrue(signals.onActivityResumed())
  }

  @Test
  fun stoppingResetsNetworkBaseline() {
    val signals = SyncSignalState<String>()
    signals.onNetworkStarted("wifi")
    signals.onActivityStopped()

    assertFalse(signals.onNetworkAvailable("wifi"))
  }
}
