package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import platform.Security.SecCertificateCopyData
import platform.Security.SecTrustCopyCertificateChain
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustRef

internal actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Darwin) {
        engine {
            handleChallenge { _, _, challenge, completionHandler -> trustPinnedCertificate(challenge, completionHandler) }
        }
        block()
    }

/** iOS's own trust, plus any certificate the user trusted for the host presenting it. */
@OptIn(ExperimentalForeignApi::class)
private fun trustPinnedCertificate(
    challenge: NSURLAuthenticationChallenge,
    completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
) {
    val space = challenge.protectionSpace
    val trust = space.serverTrust
    if (space.authenticationMethod != NSURLAuthenticationMethodServerTrust || trust == null || SecTrustEvaluateWithError(trust, null)) {
        completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
        return
    }
    val fingerprint = leafFingerprint(trust)
    if (fingerprint != null && TrustedCertificates.trusts(space.host, fingerprint)) {
        completionHandler(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(trust))
        return
    }
    fingerprint?.let { TrustedCertificates.refused(PresentedCertificate(space.host, it)) }
    completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
}

@OptIn(ExperimentalForeignApi::class)
private fun leafFingerprint(trust: SecTrustRef): String? {
    val chain = SecTrustCopyCertificateChain(trust) ?: return null
    try {
        if (CFArrayGetCount(chain) < 1) return null
        val data = SecCertificateCopyData(CFArrayGetValueAtIndex(chain, 0)?.reinterpret()) ?: return null
        try {
            val bytes = CFDataGetBytePtr(data)?.reinterpret<ByteVar>()?.readBytes(CFDataGetLength(data).toInt()) ?: return null
            return certificateFingerprint(bytes)
        } finally {
            CFRelease(data)
        }
    } finally {
        CFRelease(chain)
    }
}
