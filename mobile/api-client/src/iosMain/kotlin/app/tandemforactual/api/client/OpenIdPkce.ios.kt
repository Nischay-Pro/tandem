package app.tandemforactual.api.client

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0)
    val bytes = ByteArray(size)
    val status =
        bytes.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, size.convert(), pinned.addressOf(0))
        }
    check(status == 0) { "The system random source is unavailable." }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun sha256(input: ByteArray): ByteArray {
    require(input.isNotEmpty())
    val digest = ByteArray(32)
    input.usePinned { inputPinned ->
        digest.usePinned { digestPinned ->
            CC_SHA256(
                inputPinned.addressOf(0).reinterpret<UByteVar>(),
                input.size.convert(),
                digestPinned.addressOf(0).reinterpret<UByteVar>(),
            )
        }
    }
    return digest
}
