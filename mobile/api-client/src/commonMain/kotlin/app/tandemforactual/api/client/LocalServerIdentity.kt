package app.tandemforactual.api.client

internal const val LOCAL_IDENTITY_CONTEXT = "tandem-local-identity-v1"
private const val SHA256_BLOCK_BYTES = 64
private const val LOCAL_IDENTITY_NONCE_BYTES = 32

internal fun hmacSha256(
    key: ByteArray,
    message: ByteArray,
): ByteArray {
    val normalizedKey = (if (key.size > SHA256_BLOCK_BYTES) sha256(key) else key).copyOf(SHA256_BLOCK_BYTES)
    val inner = ByteArray(SHA256_BLOCK_BYTES) { (normalizedKey[it].toInt() xor 0x36).toByte() }
    val outer = ByteArray(SHA256_BLOCK_BYTES) { (normalizedKey[it].toInt() xor 0x5c).toByte() }
    return sha256(outer + sha256(inner + message))
}

internal fun localIdentityProof(
    accessToken: String,
    deviceId: String,
    nonce: String,
): String {
    val credentialHash =
        sha256(
            accessToken.encodeToByteArray(),
        ).joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    return encodeBase64Url(hmacSha256(credentialHash.encodeToByteArray(), "$LOCAL_IDENTITY_CONTEXT\n$deviceId\n$nonce".encodeToByteArray()))
}

internal fun createLocalIdentityNonce(): String = encodeBase64Url(secureRandomBytes(LOCAL_IDENTITY_NONCE_BYTES))

internal fun constantTimeEquals(
    first: String,
    second: String,
): Boolean {
    if (first.length != second.length) return false
    var difference = 0
    for (index in first.indices) difference = difference or (first[index].code xor second[index].code)
    return difference == 0
}
