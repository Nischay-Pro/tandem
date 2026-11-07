package app.tandemforactual.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.Foundation.NSNotificationCenter
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.darwin.dispatch_get_main_queue

/** App lifecycle and network-recovery signals for the iOS Compose host. */
internal class IosSyncEventSource {
    private val mutableAppResumeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val mutableNetworkRestoredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val appActive = MutableStateFlow(true)
    private var pathAvailable: Boolean? = null
    private var hasBeenActive = false
    private val pathMonitor = nw_path_monitor_create()
    private val becameActiveObserver =
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) { _ ->
            appActive.value = true
            if (hasBeenActive) mutableAppResumeEvents.tryEmit(Unit)
            hasBeenActive = true
        }
    private val willResignActiveObserver =
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = null,
        ) { _ -> appActive.value = false }

    val appResumeEvents = mutableAppResumeEvents.asSharedFlow()
    val networkRestoredEvents = mutableNetworkRestoredEvents.asSharedFlow()

    fun close() {
        NSNotificationCenter.defaultCenter.removeObserver(becameActiveObserver)
        NSNotificationCenter.defaultCenter.removeObserver(willResignActiveObserver)
        nw_path_monitor_cancel(pathMonitor)
    }

    init {
        nw_path_monitor_set_queue(pathMonitor, dispatch_get_main_queue())
        nw_path_monitor_set_update_handler(pathMonitor) { path ->
            val isAvailable = nw_path_get_status(path) == nw_path_status_satisfied
            val wasAvailable = pathAvailable
            pathAvailable = isAvailable
            if (wasAvailable == false && isAvailable && appActive.value) {
                mutableNetworkRestoredEvents.tryEmit(Unit)
            }
        }
        nw_path_monitor_start(pathMonitor)
    }
}
