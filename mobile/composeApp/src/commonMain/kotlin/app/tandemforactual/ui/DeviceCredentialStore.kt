package app.tandemforactual.ui

/** Platform-backed storage for the bearer credential returned after device pairing. */
interface DeviceCredentialStore {
    fun read(): String?

    fun write(value: String)

    fun clear()
}
