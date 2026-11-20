package app.tandemforactual.ui

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecInteractionNotAllowed
import platform.Security.errSecSuccess
import platform.Security.kSecAccessControlBiometryCurrentSet
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecUseAuthenticationContext
import platform.Security.kSecValueData
import platform.darwin.DISPATCH_QUEUE_PRIORITY_HIGH
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

/** Face ID and Touch ID: secrets live in the Keychain, readable only after a successful biometric check on this device. */
@OptIn(ExperimentalForeignApi::class)
class IosDeviceAuthenticator : DeviceAuthenticator {
    override fun isAvailable(): Boolean = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)

    override fun authenticate(
        title: String,
        onResult: (Boolean) -> Unit,
    ) {
        LAContext().evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = title) { success, _ ->
            onMain { onResult(success) }
        }
    }

    override fun canStoreSecrets(): Boolean = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)

    override fun hasStoredSecret(id: String): Boolean {
        val context = LAContext().apply { interactionNotAllowed = true }
        val status =
            withQuery(id) { query ->
                query.putObject(kSecUseAuthenticationContext, context)
                SecItemCopyMatching(query.ref, null)
            }
        return status == errSecSuccess || status == errSecInteractionNotAllowed
    }

    override fun storeSecret(
        id: String,
        secret: String,
        title: String,
        onResult: (Boolean) -> Unit,
    ) {
        deleteSecret(id)
        val access =
            SecAccessControlCreateWithFlags(
                kCFAllocatorDefault,
                kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
                kSecAccessControlBiometryCurrentSet,
                null,
            )
        if (access == null) {
            onResult(false)
            return
        }
        val data = NSString.create(string = secret).dataUsingEncoding(NSUTF8StringEncoding)
        val status =
            withQuery(id) { query ->
                query.put(kSecAttrAccessControl, access)
                query.putObject(kSecValueData, data)
                SecItemAdd(query.ref, null)
            }
        CFRelease(access)
        onResult(status == errSecSuccess)
    }

    override fun readSecret(
        id: String,
        title: String,
        onResult: (String?) -> Unit,
    ) {
        val context = LAContext().apply { localizedReason = title }
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH.toLong(), 0u)) {
            val secret =
                memScoped {
                    val result = alloc<CFTypeRefVar>()
                    val status =
                        withQuery(id) { query ->
                            query.put(kSecReturnData, kCFBooleanTrue)
                            query.put(kSecMatchLimit, kSecMatchLimitOne)
                            query.putObject(kSecUseAuthenticationContext, context)
                            SecItemCopyMatching(query.ref, result.ptr)
                        }
                    if (status != errSecSuccess) {
                        null
                    } else {
                        (
                            CFBridgingRelease(
                                result.value,
                            ) as? NSData
                        )?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
                    }
                }
            onMain { onResult(secret) }
        }
    }

    override fun deleteSecret(id: String) {
        withQuery(id) { query -> SecItemDelete(query.ref) }
    }

    override fun deleteAllSecrets() {
        withQuery(null) { query -> SecItemDelete(query.ref) }
    }

    private fun <T> withQuery(
        account: String?,
        block: (KeychainQuery) -> T,
    ): T {
        val query = KeychainQuery()
        return try {
            query.put(kSecClass, kSecClassGenericPassword)
            query.putObject(kSecAttrService, SERVICE)
            account?.let { query.putObject(kSecAttrAccount, it) }
            block(query)
        } finally {
            query.release()
        }
    }

    private class KeychainQuery {
        val ref: CFMutableDictionaryRef? =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        private val bridged = mutableListOf<CFTypeRef?>()

        fun put(
            key: CValuesRef<*>?,
            value: CValuesRef<*>?,
        ) = CFDictionaryAddValue(ref, key, value)

        fun putObject(
            key: CValuesRef<*>?,
            value: Any?,
        ) {
            val retained = CFBridgingRetain(value)
            bridged += retained
            CFDictionaryAddValue(ref, key, retained)
        }

        fun release() {
            bridged.forEach { it?.let(::CFRelease) }
            CFRelease(ref)
        }
    }

    private fun onMain(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)

    private companion object {
        const val SERVICE = "app.tandemforactual.budget-passwords"
    }
}
