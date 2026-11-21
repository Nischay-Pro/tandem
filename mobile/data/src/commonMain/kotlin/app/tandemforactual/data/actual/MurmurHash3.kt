package app.tandemforactual.data.actual

/** MurmurHash3 (x86, 32-bit, seed 0) over a string's low byte per character, as the `murmurhash` package Actual uses. */
internal fun murmurHash3(key: String): Long {
    val c1 = 0xcc9e2d51.toInt()
    val c2 = 0x1b873593
    var h = 0
    val remainder = key.length and 3
    val blocks = key.length - remainder
    var i = 0
    while (i < blocks) {
        var k =
            (key[i].code and 0xff) or
                ((key[i + 1].code and 0xff) shl 8) or
                ((key[i + 2].code and 0xff) shl 16) or
                ((key[i + 3].code and 0xff) shl 24)
        k *= c1
        k = k.rotateLeft(15)
        k *= c2
        h = h xor k
        h = h.rotateLeft(13)
        h = h * 5 + 0xe6546b64.toInt()
        i += 4
    }
    var k = 0
    if (remainder == 3) k = k xor ((key[i + 2].code and 0xff) shl 16)
    if (remainder >= 2) k = k xor ((key[i + 1].code and 0xff) shl 8)
    if (remainder >= 1) {
        k = k xor (key[i].code and 0xff)
        k *= c1
        k = k.rotateLeft(15)
        k *= c2
        h = h xor k
    }
    h = h xor key.length
    h = h xor (h ushr 16)
    h *= 0x85ebca6b.toInt()
    h = h xor (h ushr 13)
    h *= 0xc2b2ae35.toInt()
    h = h xor (h ushr 16)
    return h.toLong() and 0xffffffffL
}
