package app.tandemforactual.ui

enum class SyncNetwork(
    val shortLabel: String,
    val detail: String,
) {
    WIFI("Wi-Fi", "Syncs on Wi-Fi. Hotspots and metered Wi-Fi count as mobile data."),
    MOBILE_DATA("Mobile", "Syncs only on mobile data and other metered connections."),
    ANY("Both", "Syncs on any connection."),
    ;

    fun allows(network: NetworkKind?): Boolean =
        when {
            network == null || this == ANY -> true
            this == WIFI -> network == NetworkKind.UNMETERED
            else -> network == NetworkKind.METERED
        }

    companion object {
        fun restore(
            name: String?,
            legacyWifiOnly: Boolean?,
        ): SyncNetwork? =
            entries.firstOrNull { it.name == name }
                ?: legacyWifiOnly?.let { if (it) WIFI else ANY }
    }
}

enum class NetworkKind {
    UNMETERED,
    METERED,
}
