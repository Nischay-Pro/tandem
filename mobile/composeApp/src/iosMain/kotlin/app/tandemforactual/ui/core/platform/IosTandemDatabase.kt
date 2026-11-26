package app.tandemforactual.ui.core.platform

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder

/**
 * The app and its background task share one database for the whole process: Room on iOS has no multi-instance
 * invalidation, so a second instance's writes would never reach the screens observing the first.
 */
internal val iosTandemDatabase: TandemDatabase by lazy { openTandemDatabase(tandemDatabaseBuilder()) }
