package app.tandemforactual.android

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import app.tandemforactual.ui.app.AuthReturnEvents

/**
 * Opens an OpenID provider's page in an Auth Tab, which hands the sign-in callback back to this activity alone instead
 * of to any app that claims Tandem's link scheme. Browsers without Auth Tab get the page in the browser as before.
 */
internal class AndroidSignInBrowser(
    private val activity: ComponentActivity,
) {
    private var onCancelled: (() -> Unit)? = null
    private val launcher =
        AuthTabIntent.registerActivityResultLauncher(activity) { result ->
            val cancelled = onCancelled
            onCancelled = null
            val returned = result.resultUri.takeIf { result.resultCode == AuthTabIntent.RESULT_OK }
            if (returned != null) AuthReturnEvents.publish(returned.toString()) else cancelled?.invoke()
        }

    fun open(
        url: String,
        onCancelled: () -> Unit,
    ) {
        val page = Uri.parse(url)
        val browser = CustomTabsClient.getPackageName(activity, null)
        if (browser != null && CustomTabsClient.isAuthTabSupported(activity, browser)) {
            this.onCancelled = onCancelled
            AuthTabIntent.Builder().build().launch(launcher, page, SIGN_IN_SCHEME)
        } else {
            activity.startActivity(Intent(Intent.ACTION_VIEW, page))
        }
    }

    private companion object {
        const val SIGN_IN_SCHEME = "app.tandemforactual"
    }
}
