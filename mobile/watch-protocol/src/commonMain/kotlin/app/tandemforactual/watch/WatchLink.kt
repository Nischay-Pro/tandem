package app.tandemforactual.watch

/** The phone's end of the watch connection; [publish] with null clears what the watch shows. */
interface WatchLink {
    suspend fun publish(summary: WatchSummary?)

    /** Whether a paired watch has Tandem installed, reachable or not. */
    suspend fun hasWatch(): Boolean
}
