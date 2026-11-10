package app.tandemforactual.ui

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
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
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

internal class IosCredentialUnavailableBeforeFirstUnlockException(
    status: Int,
) : Exception("Device credential is unavailable before the first unlock (OSStatus $status)")

internal data class IosKeychainReadResult(
    val status: Int,
    val credential: String? = null,
)

/** Small boundary around Security.framework so the store behavior can be tested without Keychain entitlements. */
internal interface IosKeychainClient {
    fun read(): IosKeychainReadResult

    fun add(value: String): Int

    fun update(value: String): Int

    fun delete(): Int
}

internal class IosDeviceCredentialStore(
    private val keychain: IosKeychainClient = SecurityIosKeychainClient(),
) : DeviceCredentialStore {
    override fun read(): String? {
        val result = keychain.read()
        return when (result.status) {
            errSecItemNotFound -> null
            errSecInteractionNotAllowed -> throw IosCredentialUnavailableBeforeFirstUnlockException(result.status)
            errSecSuccess -> result.credential ?: error("Stored device credential is invalid (OSStatus ${result.status})")
            else -> error("Could not read device credential from Keychain (OSStatus ${result.status})")
        }
    }

    override fun write(value: String) {
        val addStatus = keychain.add(value)
        if (addStatus == errSecSuccess) return
        if (addStatus != errSecDuplicateItem) error("Could not save device credential to Keychain (OSStatus $addStatus)")

        val updateStatus = keychain.update(value)
        check(updateStatus == errSecSuccess) { "Could not update device credential in Keychain (OSStatus $updateStatus)" }
    }

    override fun clear() {
        val status = keychain.delete()
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "Could not remove device credential from Keychain (OSStatus $status)"
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class SecurityIosKeychainClient : IosKeychainClient {
    override fun read(): IosKeychainReadResult =
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = withKeychainQuery(includeReadResult = true) { query -> SecItemCopyMatching(query, result.ptr) }
            if (status != errSecSuccess) return@memScoped IosKeychainReadResult(status)

            val data =
                CFBridgingRelease(result.value) as? NSData
                    ?: return@memScoped IosKeychainReadResult(status)
            val string =
                NSString.create(data, NSUTF8StringEncoding)
                    ?: return@memScoped IosKeychainReadResult(status)
            IosKeychainReadResult(status, string.UTF8String()?.toKString())
        }

    override fun add(value: String): Int {
        val data = value.toKeychainData()
        return withKeychainQuery(data = data) { query -> SecItemAdd(query, null) }
    }

    override fun update(value: String): Int {
        val data = value.toKeychainData()
        return withKeychainQuery { query ->
            withKeychainQuery(includeIdentity = false, data = data) { updates -> SecItemUpdate(query, updates) }
        }
    }

    override fun delete(): Int = withKeychainQuery { query -> SecItemDelete(query) }

    private fun String.toKeychainData(): NSData =
        NSString.create(this).dataUsingEncoding(NSUTF8StringEncoding)
            ?: error("Could not encode device credential for Keychain")

    private inline fun <T> withKeychainQuery(
        includeIdentity: Boolean = true,
        includeReadResult: Boolean = false,
        data: NSData? = null,
        block: (CFDictionaryRef) -> T,
    ): T {
        val query = NSMutableDictionary()
        if (includeIdentity) {
            query.setObject(kSecClassGenericPassword.asNSString(), forKey = kSecClass.asNSString())
            query.setObject(NSString.create(KEYCHAIN_SERVICE), forKey = kSecAttrService.asNSString())
            query.setObject(NSString.create(KEYCHAIN_ACCOUNT), forKey = kSecAttrAccount.asNSString())
        }
        if (includeReadResult) {
            query.setObject(NSNumber.numberWithBool(true), forKey = kSecReturnData.asNSString())
            query.setObject(kSecMatchLimitOne.asNSString(), forKey = kSecMatchLimit.asNSString())
        }
        if (data != null) {
            query.setObject(data, forKey = kSecValueData.asNSString())
            query.setObject(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly.asNSString(), forKey = kSecAttrAccessible.asNSString())
        }

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
        const val KEYCHAIN_ACCOUNT = "authenticated-device"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CFStringRef?.asNSString(): NSString =
    interpretObjCPointer(this?.rawValue ?: error("Required Security string constant is unavailable"))
