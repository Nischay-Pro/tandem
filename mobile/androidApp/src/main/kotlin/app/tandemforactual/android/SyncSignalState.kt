package app.tandemforactual.android

/** Tracks the Android lifecycle and default-network transitions that can trigger a budget sync. */
internal class SyncSignalState<Network> {
  private var activeNetwork: Network? = null
  private var networkStateInitialized = false
  private var hasBeenResumed = false

  fun onNetworkStarted(activeNetwork: Network?) {
    this.activeNetwork = activeNetwork
    networkStateInitialized = true
  }

  fun onNetworkAvailable(network: Network): Boolean {
    val restored = networkStateInitialized && activeNetwork == null
    activeNetwork = network
    networkStateInitialized = true
    return restored
  }

  fun onNetworkLost(network: Network) {
    if (activeNetwork == network) activeNetwork = null
  }

  fun onActivityResumed(): Boolean {
    val resumedBefore = hasBeenResumed
    hasBeenResumed = true
    return resumedBefore
  }

  fun onActivityStopped() {
    activeNetwork = null
    networkStateInitialized = false
  }
}
