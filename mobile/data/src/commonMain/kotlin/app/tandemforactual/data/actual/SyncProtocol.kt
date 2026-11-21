package app.tandemforactual.data.actual

/** One field change in Actual's sync protocol. Values use Actual's typed encoding, such as `N:-5000` or `S:name`. */
data class ActualMessage(
    val dataset: String,
    val row: String,
    val column: String,
    val value: String,
)

data class MessageEnvelope(
    val timestamp: String,
    val isEncrypted: Boolean,
    val content: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is MessageEnvelope && timestamp == other.timestamp && isEncrypted == other.isEncrypted && content.contentEquals(other.content)

    override fun hashCode(): Int = (timestamp.hashCode() * 31 + isEncrypted.hashCode()) * 31 + content.contentHashCode()
}

data class EncryptedData(
    val iv: ByteArray,
    val authTag: ByteArray,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is EncryptedData && iv.contentEquals(other.iv) && authTag.contentEquals(other.authTag) && data.contentEquals(other.data)

    override fun hashCode(): Int = (iv.contentHashCode() * 31 + authTag.contentHashCode()) * 31 + data.contentHashCode()
}

data class SyncRequest(
    val messages: List<MessageEnvelope> = emptyList(),
    val fileId: String = "",
    val groupId: String = "",
    val keyId: String = "",
    val since: String = "",
)

data class SyncResponse(
    val messages: List<MessageEnvelope>,
    val merkle: String,
)

class ProtocolException(
    message: String,
) : IllegalArgumentException(message)

/** Encodes and decodes the protobuf messages in Actual's `sync.proto`, byte for byte as Actual's own client. */
object SyncProtocol {
    fun encode(message: ActualMessage): ByteArray =
        ProtoWriter()
            .string(1, message.dataset)
            .string(2, message.row)
            .string(3, message.column)
            .string(4, message.value)
            .bytes()

    fun decodeMessage(bytes: ByteArray): ActualMessage {
        var dataset = ""
        var row = ""
        var column = ""
        var value = ""
        ProtoReader(bytes).forEachField { field, reader ->
            when (field) {
                1 -> dataset = reader.string()
                2 -> row = reader.string()
                3 -> column = reader.string()
                4 -> value = reader.string()
                else -> reader.skip()
            }
        }
        return ActualMessage(dataset, row, column, value)
    }

    fun encode(envelope: MessageEnvelope): ByteArray =
        ProtoWriter()
            .string(1, envelope.timestamp)
            .bool(2, envelope.isEncrypted)
            .bytes(3, envelope.content)
            .bytes()

    fun decodeEnvelope(bytes: ByteArray): MessageEnvelope {
        var timestamp = ""
        var encrypted = false
        var content = ByteArray(0)
        ProtoReader(bytes).forEachField { field, reader ->
            when (field) {
                1 -> timestamp = reader.string()
                2 -> encrypted = reader.varint() != 0L
                3 -> content = reader.lengthDelimited()
                else -> reader.skip()
            }
        }
        return MessageEnvelope(timestamp, encrypted, content)
    }

    fun encode(data: EncryptedData): ByteArray =
        ProtoWriter()
            .bytes(1, data.iv)
            .bytes(2, data.authTag)
            .bytes(3, data.data)
            .bytes()

    fun decodeEncryptedData(bytes: ByteArray): EncryptedData {
        var iv = ByteArray(0)
        var tag = ByteArray(0)
        var data = ByteArray(0)
        ProtoReader(bytes).forEachField { field, reader ->
            when (field) {
                1 -> iv = reader.lengthDelimited()
                2 -> tag = reader.lengthDelimited()
                3 -> data = reader.lengthDelimited()
                else -> reader.skip()
            }
        }
        return EncryptedData(iv, tag, data)
    }

    fun encode(request: SyncRequest): ByteArray {
        val writer = ProtoWriter()
        request.messages.forEach { writer.message(1, encode(it)) }
        return writer
            .string(2, request.fileId)
            .string(3, request.groupId)
            .string(5, request.keyId)
            .string(6, request.since)
            .bytes()
    }

    fun decodeRequest(bytes: ByteArray): SyncRequest {
        val messages = mutableListOf<MessageEnvelope>()
        var fileId = ""
        var groupId = ""
        var keyId = ""
        var since = ""
        ProtoReader(bytes).forEachField { field, reader ->
            when (field) {
                1 -> messages += decodeEnvelope(reader.lengthDelimited())
                2 -> fileId = reader.string()
                3 -> groupId = reader.string()
                5 -> keyId = reader.string()
                6 -> since = reader.string()
                else -> reader.skip()
            }
        }
        return SyncRequest(messages, fileId, groupId, keyId, since)
    }

    fun encode(response: SyncResponse): ByteArray {
        val writer = ProtoWriter()
        response.messages.forEach { writer.message(1, encode(it)) }
        return writer.string(2, response.merkle).bytes()
    }

    fun decodeResponse(bytes: ByteArray): SyncResponse {
        val messages = mutableListOf<MessageEnvelope>()
        var merkle = ""
        ProtoReader(bytes).forEachField { field, reader ->
            when (field) {
                1 -> messages += decodeEnvelope(reader.lengthDelimited())
                2 -> merkle = reader.string()
                else -> reader.skip()
            }
        }
        return SyncResponse(messages, merkle)
    }
}

private class ProtoWriter {
    private val out = mutableListOf<Byte>()

    fun string(
        field: Int,
        value: String,
    ): ProtoWriter = if (value.isEmpty()) this else bytes(field, value.encodeToByteArray())

    fun bool(
        field: Int,
        value: Boolean,
    ): ProtoWriter {
        if (!value) return this
        varint((field shl 3).toLong())
        varint(1)
        return this
    }

    fun bytes(
        field: Int,
        value: ByteArray,
    ): ProtoWriter = if (value.isEmpty()) this else message(field, value)

    fun message(
        field: Int,
        value: ByteArray,
    ): ProtoWriter {
        varint(((field shl 3) or 2).toLong())
        varint(value.size.toLong())
        value.forEach { out += it }
        return this
    }

    fun bytes(): ByteArray = out.toByteArray()

    private fun varint(value: Long) {
        var v = value
        while (v and 0x7fL.inv() != 0L) {
            out += ((v and 0x7f) or 0x80).toByte()
            v = v ushr 7
        }
        out += v.toByte()
    }
}

private class ProtoReader(
    private val bytes: ByteArray,
) {
    private var position = 0
    private var wireType = 0

    fun forEachField(handle: (Int, ProtoReader) -> Unit) {
        while (position < bytes.size) {
            val tag = varint()
            wireType = (tag and 7).toInt()
            handle((tag ushr 3).toInt(), this)
        }
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (position >= bytes.size) throw ProtocolException("truncated varint")
            if (shift >= 64) throw ProtocolException("varint too long")
            val b = bytes[position++].toLong() and 0xff
            result = result or ((b and 0x7f) shl shift)
            if (b and 0x80 == 0L) return result
            shift += 7
        }
    }

    fun lengthDelimited(): ByteArray {
        if (wireType != 2) throw ProtocolException("expected length-delimited field")
        val length = varint()
        if (length < 0 || length > bytes.size - position) throw ProtocolException("field length out of range")
        val start = position
        position += length.toInt()
        return bytes.copyOfRange(start, position)
    }

    fun string(): String = lengthDelimited().decodeToString()

    fun skip() {
        when (wireType) {
            0 -> varint()
            1 -> advance(8)
            2 -> lengthDelimited()
            5 -> advance(4)
            else -> throw ProtocolException("unsupported wire type $wireType")
        }
    }

    private fun advance(count: Int) {
        if (count > bytes.size - position) throw ProtocolException("truncated field")
        position += count
    }
}
