package app.tandemforactual.ui.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.systemBackgroundColor

/** Face ID and passcode sheets make the app inactive too; covering for them would only flash the cover behind the prompt. */
internal var iosAuthenticationPrompts = 0

/** Covers the app before iOS snapshots it for the app switcher, so balances never show there. */
@OptIn(ExperimentalForeignApi::class)
internal class IosPrivacyCover(
    private val host: UIViewController,
) {
    private var cover: UIView? = null
    private val center = NSNotificationCenter.defaultCenter
    private val observers =
        listOf(
            center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
                if (iosAuthenticationPrompts == 0) show()
            },
            center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ -> show() },
            center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ -> hide() },
        )

    private fun show() {
        if (cover != null) return
        val root = host.viewIfLoaded?.window ?: host.viewIfLoaded ?: return
        val view = UIView(frame = root.bounds)
        view.backgroundColor = UIColor.systemBackgroundColor
        view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        root.addSubview(view)
        cover = view
    }

    private fun hide() {
        cover?.removeFromSuperview()
        cover = null
    }

    fun close() {
        hide()
        observers.forEach { center.removeObserver(it) }
    }
}
