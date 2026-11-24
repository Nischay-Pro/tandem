package app.tandemforactual.data.actual

class ZipFormatException(
    message: String,
) : IllegalArgumentException(message)

/** Reads the stored and deflated entries of a zip file, such as the budget files Actual's server returns. */
object ZipArchive {
    fun read(
        bytes: ByteArray,
        maxEntryBytes: Long = 512L * 1024 * 1024,
    ): Map<String, ByteArray> {
        val end = findEndOfCentralDirectory(bytes)
        val count = u16(bytes, end + 10)
        var offset = u32(bytes, end + 16).toInt()
        val entries = LinkedHashMap<String, ByteArray>()
        repeat(count) {
            if (u32(bytes, offset) != 0x02014b50L) throw ZipFormatException("bad central directory")
            val method = u16(bytes, offset + 10)
            val compressedSize = u32(bytes, offset + 20)
            val size = u32(bytes, offset + 24)
            val nameLength = u16(bytes, offset + 28)
            val extraLength = u16(bytes, offset + 30)
            val commentLength = u16(bytes, offset + 32)
            val localOffset = u32(bytes, offset + 42).toInt()
            val name = bytes.copyOfRange(offset + 46, offset + 46 + nameLength).decodeToString()
            offset += 46 + nameLength + extraLength + commentLength
            if (size > maxEntryBytes) throw ZipFormatException("entry too large")
            if (u32(bytes, localOffset) != 0x04034b50L) throw ZipFormatException("bad local header")
            val dataStart = localOffset + 30 + u16(bytes, localOffset + 26) + u16(bytes, localOffset + 28)
            if (compressedSize > bytes.size - dataStart) throw ZipFormatException("truncated entry")
            val data = bytes.copyOfRange(dataStart, dataStart + compressedSize.toInt())
            val content =
                when (method) {
                    0 -> data
                    8 -> Inflater.inflate(data, size.toInt())
                    else -> throw ZipFormatException("unsupported compression $method")
                }
            if (content.size.toLong() != size) throw ZipFormatException("size mismatch")
            if (!name.endsWith("/")) entries[name] = content
        }
        return entries
    }

    /** A zip of [entries] stored without compression, which every unzip tool and Actual's import can read. */
    fun write(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteSink(entries.values.sumOf { it.size } + 1024)
        val central = ByteSink(256)
        for ((name, data) in entries) {
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            val offset = out.size.toLong()
            out.header(0x04034b50L, crc, data.size, nameBytes.size, central = false)
            out.bytes(nameBytes)
            out.bytes(data)
            central.header(0x02014b50L, crc, data.size, nameBytes.size, central = true)
            central.u32(offset)
            central.bytes(nameBytes)
        }
        val centralStart = out.size.toLong()
        out.bytes(central.toByteArray())
        out.u32(0x06054b50L)
        out.u16(0)
        out.u16(0)
        out.u16(entries.size)
        out.u16(entries.size)
        out.u32(central.size.toLong())
        out.u32(centralStart)
        out.u16(0)
        return out.toByteArray()
    }

    private class ByteSink(
        capacity: Int,
    ) {
        private var buffer = ByteArray(capacity)
        var size = 0
            private set

        fun bytes(data: ByteArray) {
            if (size + data.size > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + data.size))
            data.copyInto(buffer, size)
            size += data.size
        }

        fun u16(value: Int) = bytes(byteArrayOf(value.toByte(), (value shr 8).toByte()))

        fun u32(value: Long) = bytes(ByteArray(4) { (value shr (8 * it)).toByte() })

        /** A local or central file header up to the name, stored, UTF-8 names, dated 1980-01-01. */
        fun header(
            signature: Long,
            crc: Long,
            length: Int,
            nameLength: Int,
            central: Boolean,
        ) {
            u32(signature)
            if (central) u16(20)
            u16(20)
            u16(0x0800)
            u16(0)
            u16(0)
            u16(0x21)
            u32(crc)
            u32(length.toLong())
            u32(length.toLong())
            u16(nameLength)
            u16(0)
            if (central) {
                repeat(3) { u16(0) }
                u32(0)
            }
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)
    }

    private val crcTable =
        LongArray(256) { n ->
            var c = n.toLong()
            repeat(8) { c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1 }
            c
        }

    internal fun crc32(data: ByteArray): Long {
        var c = 0xFFFFFFFFL
        for (b in data) c = crcTable[((c xor b.toLong()) and 0xFF).toInt()] xor (c ushr 8)
        return c xor 0xFFFFFFFFL
    }

    private fun findEndOfCentralDirectory(bytes: ByteArray): Int {
        var i = bytes.size - 22
        while (i >= 0 && i >= bytes.size - 22 - 0xffff) {
            if (u32(bytes, i) == 0x06054b50L) return i
            i--
        }
        throw ZipFormatException("not a zip file")
    }

    private fun u16(
        b: ByteArray,
        o: Int,
    ): Int {
        if (o < 0 || o + 2 > b.size) throw ZipFormatException("truncated zip")
        return (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
    }

    private fun u32(
        b: ByteArray,
        o: Int,
    ): Long {
        if (o < 0 || o + 4 > b.size) throw ZipFormatException("truncated zip")
        return (u16(b, o).toLong()) or (u16(b, o + 2).toLong() shl 16)
    }
}

/** Raw DEFLATE (RFC 1951) decoder. */
internal object Inflater {
    private val lengthBase =
        intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val lengthExtra = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val distBase =
        intArrayOf(
            1,
            2,
            3,
            4,
            5,
            7,
            9,
            13,
            17,
            25,
            33,
            49,
            65,
            97,
            129,
            193,
            257,
            385,
            513,
            769,
            1025,
            1537,
            2049,
            3073,
            4097,
            6145,
            8193,
            12289,
            16385,
            24577,
        )
    private val distExtra = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
    private val codeLengthOrder = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    private class Huffman(
        lengths: IntArray,
    ) {
        val counts = IntArray(16)
        val symbols = IntArray(lengths.size)

        init {
            lengths.forEach { counts[it]++ }
            counts[0] = 0
            val offsets = IntArray(16)
            for (i in 1 until 16) offsets[i] = offsets[i - 1] + counts[i - 1]
            lengths.forEachIndexed { symbol, length -> if (length != 0) symbols[offsets[length]++] = symbol }
        }
    }

    private class Bits(
        val data: ByteArray,
    ) {
        var position = 0
        private var buffer = 0
        private var count = 0

        fun bits(n: Int): Int {
            while (count < n) {
                if (position >= data.size) throw ZipFormatException("truncated deflate data")
                buffer = buffer or ((data[position++].toInt() and 0xff) shl count)
                count += 8
            }
            val value = buffer and ((1 shl n) - 1)
            buffer = buffer ushr n
            count -= n
            return value
        }

        fun alignToByte() {
            buffer = 0
            count = 0
        }

        fun decode(h: Huffman): Int {
            var code = 0
            var first = 0
            var index = 0
            for (len in 1 until 16) {
                code = code or bits(1)
                val c = h.counts[len]
                if (code - c < first) return h.symbols[index + (code - first)]
                index += c
                first += c
                first = first shl 1
                code = code shl 1
            }
            throw ZipFormatException("bad huffman code")
        }
    }

    fun inflate(
        data: ByteArray,
        expectedSize: Int,
    ): ByteArray {
        val out = ByteArrayBuilder(expectedSize)
        val input = Bits(data)
        do {
            val last = input.bits(1)
            when (input.bits(2)) {
                0 -> {
                    input.alignToByte()
                    val p = input.position
                    if (p + 4 > data.size) throw ZipFormatException("truncated stored block")
                    val len = (data[p].toInt() and 0xff) or ((data[p + 1].toInt() and 0xff) shl 8)
                    val nlen = (data[p + 2].toInt() and 0xff) or ((data[p + 3].toInt() and 0xff) shl 8)
                    if (len != nlen.inv() and 0xffff) throw ZipFormatException("bad stored block")
                    if (p + 4 + len > data.size) throw ZipFormatException("truncated stored block")
                    out.append(data, p + 4, len)
                    input.position = p + 4 + len
                }
                1 -> block(input, out, FIXED_LITERALS, FIXED_DISTANCES)
                2 -> {
                    val (literals, distances) = dynamicTables(input)
                    block(input, out, literals, distances)
                }
                else -> throw ZipFormatException("bad block type")
            }
        } while (last == 0)
        return out.toByteArray()
    }

    private val FIXED_LITERALS =
        Huffman(
            IntArray(288) {
                if (it < 144) {
                    8
                } else if (it < 256) {
                    9
                } else if (it < 280) {
                    7
                } else {
                    8
                }
            },
        )
    private val FIXED_DISTANCES = Huffman(IntArray(30) { 5 })

    private fun dynamicTables(input: Bits): Pair<Huffman, Huffman> {
        val hlit = input.bits(5) + 257
        val hdist = input.bits(5) + 1
        val hclen = input.bits(4) + 4
        val codeLengths = IntArray(19)
        for (i in 0 until hclen) codeLengths[codeLengthOrder[i]] = input.bits(3)
        val codeTable = Huffman(codeLengths)
        val lengths = IntArray(hlit + hdist)
        var i = 0
        while (i < lengths.size) {
            val symbol = input.decode(codeTable)
            when {
                symbol < 16 -> lengths[i++] = symbol
                else -> {
                    val (value, repeat) =
                        when (symbol) {
                            16 -> {
                                if (i == 0) throw ZipFormatException("repeat without length")
                                lengths[i - 1] to 3 + input.bits(2)
                            }
                            17 -> 0 to 3 + input.bits(3)
                            else -> 0 to 11 + input.bits(7)
                        }
                    if (i + repeat > lengths.size) throw ZipFormatException("too many lengths")
                    repeat(repeat) { lengths[i++] = value }
                }
            }
        }
        return Huffman(lengths.copyOfRange(0, hlit)) to Huffman(lengths.copyOfRange(hlit, lengths.size))
    }

    private fun block(
        input: Bits,
        out: ByteArrayBuilder,
        literals: Huffman,
        distances: Huffman,
    ) {
        while (true) {
            val symbol = input.decode(literals)
            when {
                symbol < 256 -> out.append(symbol.toByte())
                symbol == 256 -> return
                else -> {
                    val index = symbol - 257
                    if (index >= lengthBase.size) throw ZipFormatException("bad length symbol")
                    val length = lengthBase[index] + input.bits(lengthExtra[index])
                    val distSymbol = input.decode(distances)
                    if (distSymbol >= distBase.size) throw ZipFormatException("bad distance symbol")
                    val distance = distBase[distSymbol] + input.bits(distExtra[distSymbol])
                    out.copyBack(distance, length)
                }
            }
        }
    }
}

internal class ByteArrayBuilder(
    initialCapacity: Int,
) {
    private var buffer = ByteArray(maxOf(16, initialCapacity))
    private var size = 0

    private fun ensure(extra: Int) {
        if (size + extra > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
    }

    fun append(b: Byte) {
        ensure(1)
        buffer[size++] = b
    }

    fun append(
        src: ByteArray,
        offset: Int,
        length: Int,
    ) {
        ensure(length)
        src.copyInto(buffer, size, offset, offset + length)
        size += length
    }

    fun copyBack(
        distance: Int,
        length: Int,
    ) {
        if (distance > size) throw ZipFormatException("distance too far back")
        ensure(length)
        for (i in 0 until length) {
            buffer[size] = buffer[size - distance]
            size++
        }
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}
