package app.tandemforactual.api.client

const val CLIENT_API_VERSION = 1

data class ServerCapabilities(
    val apiVersion: Int,
    val minimumClientApiVersion: Int,
    val features: Set<String>,
    val actualVersion: String?,
) {
    val isLegacy: Boolean get() = apiVersion == 0

    val clientUpdateRequired: Boolean get() = minimumClientApiVersion > CLIENT_API_VERSION

    fun supports(feature: String): Boolean = feature in features

    companion object {
        val LEGACY = ServerCapabilities(apiVersion = 0, minimumClientApiVersion = 0, features = emptySet(), actualVersion = null)
    }
}
