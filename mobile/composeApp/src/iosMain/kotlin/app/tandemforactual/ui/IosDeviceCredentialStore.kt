package app.tandemforactual.ui

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.UTF8String
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.numberWithBool
import platform.Foundation.setObject
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecInteractionNotAllowed
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess

internal class IosCredentialUnavailableBeforeFirstUnlockException : Exception()

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosDeviceCredentialStore : DeviceCredentialStore {
    override fun read(): String? =
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status =
                withKeychainQuery(includeReadResult = true) { query ->
                    SecItemCopyMatching(query, result.ptr)
                }
            when (status) {
                errSecItemNotFound -> null
                errSecInteractionNotAllowed -> throw IosCredentialUnavailableBeforeFirstUnlockException()
                errSecSuccess -> {
                    val data = CFBridgingRelease(result.value) as? NSData ?: error("Stored device credential is invalid")
                    val string = NSString.create(data, NSUTF8StringEncoding) ?: error("Stored device credential is invalid")
                    string.UTF8String()?.toKString() ?: error("Stored device credential is invalid")
                }
                else -> error("Could not read device credential from Keychain")
            }
        }

    override fun write(value: String) {
        val data =
            NSString.create(value).dataUsingEncoding(NSUTF8StringEncoding)
                ?: error("Could not encode device credential for Keychain")
        val addStatus = withKeychainQuery(data = data) { query -> SecItemAdd(query, null) }
        if (addStatus == errSecSuccess) return
        if (addStatus != errSecDuplicateItem) error("Could not save device credential to Keychain")

        val updateStatus =
            withKeychainQuery { query ->
                withKeychainQuery(includeIdentity = false, data = data) { updates -> SecItemUpdate(query, updates) }
            }
        check(updateStatus == errSecSuccess) { "Could not update device credential in Keychain" }
    }

    override fun clear() {
        val status = withKeychainQuery { query -> SecItemDelete(query) }
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "Could not remove device credential from Keychain"
        }
    }

    private inline fun <T> withKeychainQuery(
        includeIdentity: Boolean = true,
        includeReadResult: Boolean = false,
        data: NSData? = null,
        block: (CFDictionaryRef) -> T,
    ): T {
        val query = NSMutableDictionary()
        if (includeIdentity) {
            query.setObject(NSString.create("genp"), forKey = NSString.create("class"))
            query.setObject(NSString.create(KEYCHAIN_SERVICE), forKey = NSString.create("svce"))
            query.setObject(NSString.create(KEYCHAIN_ACCOUNT), forKey = NSString.create("acct"))
        }
        if (includeReadResult) {
            query.setObject(NSNumber.numberWithBool(true), forKey = NSString.create("r_Data"))
            query.setObject(NSString.create("m_LimitOne"), forKey = NSString.create("m_Limit"))
        }
        if (data != null) {
            query.setObject(data, forKey = NSString.create("v_Data"))
            query.setObject(NSString.create(KEYCHAIN_ACCESSIBILITY), forKey = NSString.create("pdmn"))
        }

        // These values match Apple's Security string constants and bridge through Foundation.
        val retainedQuery = CFBridgingRetain(query) ?: error("Could not create Keychain query")
        val cfQuery: CFDictionaryRef = retainedQuery.reinterpret()
        return try {
            block(cfQuery)
        } finally {
            CFRelease(cfQuery)
        }
    }

    private companion object {
        const val KEYCHAIN_SERVICE = "app.tandemforactual.device-session"
        const val KEYCHAIN_ACCOUNT = "paired-device"
        const val KEYCHAIN_ACCESSIBILITY = "cku"
    }
}
