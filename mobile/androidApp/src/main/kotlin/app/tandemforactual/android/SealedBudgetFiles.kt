package app.tandemforactual.android

import android.content.Context
import app.tandemforactual.data.database.AndroidActualBudgetFiles
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver

/** Budget copies that keep encrypted budgets in SQLCipher, as the app and its background work both need them. */
internal fun sealedBudgetFiles(context: Context): AndroidActualBudgetFiles {
    System.loadLibrary("sqlcipher")
    return AndroidActualBudgetFiles(context, sealedDriver = { rawKey -> SQLCipherDriver(rawKey.encodeToByteArray(), null, null) })
}
