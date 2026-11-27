package app.tandemforactual.ui.core.platform

import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateBackground
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISceneActivationStateForegroundInactive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/** Tandem's window scene, preferring the one in front. */
internal fun activeWindowScene(): UIWindowScene? {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    return listOf(UISceneActivationStateForegroundActive, UISceneActivationStateForegroundInactive, UISceneActivationStateBackground)
        .firstNotNullOfOrNull { state -> scenes.firstOrNull { it.activationState == state } }
}

internal fun activeWindow(): UIWindow? {
    val scene = activeWindowScene() ?: return null
    return scene.keyWindow ?: scene.windows.filterIsInstance<UIWindow>().firstOrNull()
}

/** The view controller on top of Tandem's window, which is the one that can present another. */
internal fun topViewController(): UIViewController? {
    var presenter = activeWindow()?.rootViewController
    while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
    return presenter
}

/** Whether the screen showing Tandem is being recorded, mirrored or shared. */
internal fun isScreenCaptured(): Boolean = activeWindowScene()?.screen?.captured == true
