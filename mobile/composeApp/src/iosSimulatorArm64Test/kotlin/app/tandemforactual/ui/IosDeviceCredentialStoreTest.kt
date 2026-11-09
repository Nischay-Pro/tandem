package app.tandemforactual.ui

import platform.Security.errSecDuplicateItem
import platform.Security.errSecInteractionNotAllowed
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosDeviceCredentialStoreTest {
    @Test
    fun credentialCanBeAddedUpdatedReadAndClearedWithAKeychainFake() {
        val keychain = FakeIosKeychainClient()
        val store = IosDeviceCredentialStore(keychain)

        assertNull(store.read())

        store.write("first-device-token")
        assertEquals("first-device-token", store.read())

        store.write("updated-device-token")
        assertEquals("updated-device-token", store.read())

        store.clear()
        assertNull(store.read())
    }

    @Test
    fun readFailureIncludesTheKeychainStatus() {
        val keychain = FakeIosKeychainClient().apply { readFailure = -34018 }
        val store = IosDeviceCredentialStore(keychain)

        val failure = assertFailsWith<IllegalStateException> { store.read() }

        assertTrue(failure.message.orEmpty().contains("OSStatus -34018"))
    }

    @Test
    fun unavailableBeforeFirstUnlockIncludesTheKeychainStatus() {
        val keychain = FakeIosKeychainClient().apply { readFailure = errSecInteractionNotAllowed }
        val store = IosDeviceCredentialStore(keychain)

        val failure = assertFailsWith<IosCredentialUnavailableBeforeFirstUnlockException> { store.read() }

        assertTrue(failure.message.orEmpty().contains("OSStatus $errSecInteractionNotAllowed"))
    }

    @Test
    fun addUpdateAndDeleteFailuresIncludeTheirKeychainStatus() {
        val addStatus = -34018
        val addFailure =
            assertFailsWith<IllegalStateException> {
                IosDeviceCredentialStore(FakeIosKeychainClient().apply { addFailure = addStatus }).write("token")
            }
        assertTrue(addFailure.message.orEmpty().contains("OSStatus $addStatus"))

        val updateStatus = -25308
        val updateFailure =
            assertFailsWith<IllegalStateException> {
                IosDeviceCredentialStore(
                    FakeIosKeychainClient().apply {
                        credential = "old-token"
                        updateFailure = updateStatus
                    },
                ).write("new-token")
            }
        assertTrue(updateFailure.message.orEmpty().contains("OSStatus $updateStatus"))

        val deleteStatus = -34018
        val deleteFailure =
            assertFailsWith<IllegalStateException> {
                IosDeviceCredentialStore(FakeIosKeychainClient().apply { deleteFailure = deleteStatus }).clear()
            }
        assertTrue(deleteFailure.message.orEmpty().contains("OSStatus $deleteStatus"))
    }
}

private class FakeIosKeychainClient : IosKeychainClient {
    var credential: String? = null
    var readFailure: Int? = null
    var addFailure: Int? = null
    var updateFailure: Int? = null
    var deleteFailure: Int? = null

    override fun read(): IosKeychainReadResult {
        readFailure?.let { return IosKeychainReadResult(it) }
        val stored = credential ?: return IosKeychainReadResult(errSecItemNotFound)
        return IosKeychainReadResult(errSecSuccess, stored)
    }

    override fun add(value: String): Int {
        addFailure?.let { return it }
        if (credential != null) return errSecDuplicateItem
        credential = value
        return errSecSuccess
    }

    override fun update(value: String): Int {
        updateFailure?.let { return it }
        if (credential == null) return errSecItemNotFound
        credential = value
        return errSecSuccess
    }

    override fun delete(): Int {
        deleteFailure?.let { return it }
        if (credential == null) return errSecItemNotFound
        credential = null
        return errSecSuccess
    }
}
