package app.tandemforactual.data.actual

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZipArchiveTest {
    private fun deflate(
        data: ByteArray,
        level: Int = Deflater.DEFAULT_COMPRESSION,
    ): ByteArray {
        val deflater =
            Deflater(level, true).apply {
                setInput(data)
                finish()
            }
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (!deflater.finished()) out.write(chunk, 0, deflater.deflate(chunk))
        deflater.end()
        return out.toByteArray()
    }

    private fun zip(
        entries: Map<String, ByteArray>,
        method: Int = ZipEntry.DEFLATED,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) ->
                val entry = ZipEntry(name)
                entry.method = method
                if (method == ZipEntry.STORED) {
                    entry.size = data.size.toLong()
                    entry.crc =
                        java.util.zip
                            .CRC32()
                            .apply { update(data) }
                            .value
                }
                zip.putNextEntry(entry)
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun centralDirectory(zip: ByteArray): Int {
        val end = (zip.size - 22 downTo 0).first { zip[it] == 0x50.toByte() && zip[it + 1] == 0x4b.toByte() && zip[it + 2] == 5.toByte() }
        return (0 until 4).sumOf { (zip[end + 16 + it].toInt() and 0xff) shl (8 * it) }
    }

    private fun ByteArray.putU32(
        offset: Int,
        value: Long,
    ) = repeat(4) { this[offset + it] = (value shr (8 * it)).toByte() }

    @Test
    fun inflateGrowsItsBufferInsteadOfTrustingTheDeclaredSize() {
        val text = "hello hello".encodeToByteArray()
        assertContentEquals(text, Inflater.inflate(deflate(text), Int.MAX_VALUE))
        val large = Random(5).nextBytes(3_000_000)
        assertContentEquals(large, Inflater.inflate(deflate(large), large.size))
        val huge = zip(mapOf("db.sqlite" to text)).also { it.putU32(centralDirectory(it) + 24, 1L shl 30) }
        assertFailsWith<ZipFormatException> { ZipArchive.read(huge) }
    }

    @Test
    fun inflateStopsOnceOutputPassesTheDeclaredSize() {
        val zeros = ByteArray(100_000)
        assertFailsWith<ZipFormatException>("back references") { Inflater.inflate(deflate(zeros), 1000) }
        assertFailsWith<ZipFormatException>("stored block") { Inflater.inflate(deflate(zeros, Deflater.NO_COMPRESSION), 1000) }
        assertFailsWith<ZipFormatException>("literals") { Inflater.inflate(deflate("abcdefgh".encodeToByteArray()), 3) }
        assertEquals(1000, Inflater.inflate(deflate(zeros.copyOf(1000)), 1000).size)
        val bomb = zip(mapOf("db.sqlite" to ByteArray(10_000_000))).also { it.putU32(centralDirectory(it) + 24, 1000) }
        assertFailsWith<ZipFormatException> { ZipArchive.read(bomb) }
    }

    @Test
    fun archivesOverTheTotalLimitAreRejected() {
        val entries = mapOf("a" to ByteArray(600), "b" to ByteArray(600))
        assertEquals(2, ZipArchive.read(zip(entries), maxTotalBytes = 1200).size)
        assertFailsWith<ZipFormatException> { ZipArchive.read(zip(entries), maxTotalBytes = 1000) }
    }

    @Test
    fun entriesWithTheWrongCrcAreRejected() {
        val data = Random(3).nextBytes(5000)
        val deflated = zip(mapOf("db.sqlite" to data))
        val badCrc = deflated.copyOf().also { it[centralDirectory(it) + 16] = (it[centralDirectory(it) + 16] + 1).toByte() }
        assertFailsWith<ZipFormatException> { ZipArchive.read(badCrc) }
        val stored = zip(mapOf("db.sqlite" to data), method = ZipEntry.STORED)
        val corrupted = stored.copyOf().also { it[100] = (it[100] + 1).toByte() }
        assertContentEquals(data, ZipArchive.read(stored).getValue("db.sqlite"))
        assertFailsWith<ZipFormatException> { ZipArchive.read(corrupted) }
    }

    @Test
    fun realBudgetArchivesStillRead() {
        for (name in listOf("import-parity", "parity-envelope", "rules-parity")) {
            val bytes = checkNotNull(javaClass.getResource("/actual/$name/budget.zip")).readBytes()
            val expected = LinkedHashMap<String, ByteArray>()
            ZipInputStream(bytes.inputStream()).use { zip ->
                generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { expected[it.name] = zip.readBytes() }
            }
            val actual = ZipArchive.read(bytes)
            assertEquals(expected.keys, actual.keys)
            expected.forEach { (entry, content) -> assertContentEquals(content, actual[entry], "$name/$entry") }
        }
    }
}
