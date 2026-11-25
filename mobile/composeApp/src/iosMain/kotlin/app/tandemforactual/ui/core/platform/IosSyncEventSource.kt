package app.tandemforactual.ui.core.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.darwin.dispatch_get_main_queue

/** App lifecycle and network-recovery signals for the iOS Compose host. */
internal class IosSyncEventSource {
    private val mutableAppResumeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val mutableNetworkRestoredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val appActive = MutableStateFlow(true)
    private val foreground =
        MutableStateFlow(UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateBackground)
    private var pathAvailable: Boolean? = null
    private val pathMonitor = nw_path_monitor_create()
    private val becameActiveObserver =
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) { _ ->
            val wasInactive = !appActive.value
            appActive.value = true
            if (wasInactive) mutableAppResumeEvents.tryEmit(Unit)
        }
    private val willResignActiveObserver =
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = null,
        ) { _ -> appActive.value = false }
    private val enteredBackgroundObserver =
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null,
        ) { _ -> foreground.value = false }
    private val willEnterForegroundObserver =
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = null,
        ) { _ -> foreground.value = true }

    val appResumeEvents = mutableAppResumeEvents.asSharedFlow()

    /** False while the app is in the background, as Android's started state is, so the app lock re-engages on return. */
    val appInForeground = foreground.asStateFlow()
    val networkRestoredEvents = mutableNetworkRestoredEvents.asSharedFlow()

    fun close() {
        NSNotificationCenter.defaultCenter.removeObserver(becameActiveObserver)
        NSNotificationCenter.defaultCenter.removeObserver(willResignActiveObserver)
        NSNotificationCenter.defaultCenter.removeObserver(enteredBackgroundObserver)
        NSNotificationCenter.defaultCenter.removeObserver(willEnterForegroundObserver)
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
