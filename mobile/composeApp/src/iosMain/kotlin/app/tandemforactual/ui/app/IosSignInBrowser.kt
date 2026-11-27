package app.tandemforactual.ui.app

import app.tandemforactual.ui.core.platform.activeWindow
import app.tandemforactual.ui.core.platform.activeWindowScene
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIWindow
import platform.darwin.NSObject

private var activeSession: ASWebAuthenticationSession? = null

private class SignInAnchor :
    NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): UIWindow =
        activeWindow() ?: activeWindowScene()?.let { UIWindow(windowScene = it) } ?: UIWindow()
}

private val signInAnchor = SignInAnchor()

/**
 * Opens an OpenID provider's page in an ASWebAuthenticationSession, which hands the sign-in callback straight back to
 * this session instead of to any app that claims Tandem's link scheme.
 */
internal fun openSignInPageOnIos(
    url: String,
    onCancelled: () -> Unit,
) {
    val page = NSURL.URLWithString(url) ?: return onCancelled()
    val session =
        ASWebAuthenticationSession(page, "app.tandemforactual") { callback, _ ->
            activeSession = null
            val returned = callback?.absoluteString
            if (returned != null) AuthReturnEvents.publish(returned) else onCancelled()
        }
    session.presentationContextProvider = signInAnchor
    activeSession = session
    if (!session.start()) {
        activeSession = null
        onCancelled()
    }
}
