package app.tandemforactual.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.tandemforactual.ui.core.session.LanAccess
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Asks for local network access when an address the user gives Tandem is on their own network. */
internal class AndroidLanAccess(
    private val activity: ComponentActivity,
) : LanAccess {
    private var pending: CompletableDeferred<Unit>? = null
    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            pending?.complete(Unit)
            pending = null
        }

    override suspend fun request(address: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
        val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
        if (activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return
        if (!withContext(Dispatchers.IO) { isOnLocalNetwork(address) }) return
        val waiting =
            pending ?: CompletableDeferred<Unit>().also {
                pending = it
                launcher.launch(permission)
            }
        waiting.await()
    }
}

internal fun isOnLocalNetwork(address: String): Boolean {
    val trimmed = address.trim()
    val host =
        runCatching { URI(if ("://" in trimmed) trimmed else "http://$trimmed").host }
            .getOrNull()
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.removeSuffix(".")
            ?.takeIf(String::isNotBlank) ?: return false
    if (host.endsWith(".local", ignoreCase = true)) return true
    return runCatching { InetAddress.getAllByName(host) }.getOrDefault(emptyArray()).any(InetAddress::isOnLocalNetwork)
}

private fun InetAddress.isOnLocalNetwork(): Boolean =
    isSiteLocalAddress ||
        isLinkLocalAddress ||
        isMulticastAddress ||
        (this is Inet6Address && (address[0].toInt() and 0xfe) == 0xfc)
