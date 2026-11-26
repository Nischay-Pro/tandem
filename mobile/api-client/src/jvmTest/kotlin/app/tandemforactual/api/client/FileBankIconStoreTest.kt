package app.tandemforactual.api.client

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class FileBankIconStoreTest {
    @Test
    fun keepsIconsAndMissingDomainsAcrossInstances(): Unit =
        runBlocking {
            val directory = Files.createTempDirectory("bank-icons").resolve("nested").toString()
            val store = FileBankIconStore(directory)
            assertNull(store.icon("bank.com"))
            assertNull(store.missingSince("bank.com"))

            store.saveMissing("bank.com", 42)
            assertEquals(42, FileBankIconStore(directory).missingSince("bank.com"))

            store.saveIcon("bank.com", BankIcon(byteArrayOf(1, 2, 3), BankIconFormat.PNG))
            val reopened = FileBankIconStore(directory)
            val icon = reopened.icon("bank.com")!!
            assertEquals(BankIconFormat.PNG, icon.format)
            assertContentEquals(byteArrayOf(1, 2, 3), icon.bytes)
            assertNull(reopened.missingSince("bank.com"))
            assertNull(reopened.icon("other.com"))

            java.io.File(directory, "odd.com.missing").writeText("not a time")
            assertNull(reopened.missingSince("odd.com"))
        }

    @Test
    fun clearDeletesEveryIconAndMiss(): Unit =
        runBlocking {
            val directory = Files.createTempDirectory("bank-icons").toString()
            val store = FileBankIconStore(directory)
            store.clear()
            store.saveIcon("bank.com", BankIcon(byteArrayOf(1), BankIconFormat.PNG))
            store.saveMissing("other.com", 42)

            store.clear()

            assertNull(store.icon("bank.com"))
            assertNull(store.missingSince("other.com"))
            assertEquals(
                0,
                java.io
                    .File(directory)
                    .listFiles()!!
                    .size,
            )
        }
}
