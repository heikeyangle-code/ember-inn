package com.emberinn.engine.card.png

/** PNG 数据块：类型 + 原始数据（不包含长度/CRC，由编解码器处理）。 */
data class PngChunk(val type: String, val data: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is PngChunk && type == other.type && data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * type.hashCode() + data.contentHashCode()
}

/**
 * PNG chunk 编解码（对齐官方 src/png/encode.js + png-chunks-extract）。
 * 只增删/重排 chunk，IDAT 等像素数据原字节保留，不重新压缩。
 */
object PngChunkCodec {

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun extract(bytes: ByteArray): List<PngChunk> {
        require(bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(SIGNATURE)) { "Not a PNG file" }
        val chunks = mutableListOf<PngChunk>()
        var pos = 8
        while (pos < bytes.size) {
            require(pos + 8 <= bytes.size) { "Truncated PNG chunk header" }
            val length = readUInt32BE(bytes, pos); pos += 4
            val type = String(bytes, pos, 4, Charsets.US_ASCII); pos += 4
            require(pos + length + 4 <= bytes.size) { "Truncated PNG chunk data" }
            val data = bytes.copyOfRange(pos, pos + length); pos += length
            val crc = readUInt32BE(bytes, pos); pos += 4
            require(crc == crc32(type.toByteArray(Charsets.US_ASCII) + data)) { "PNG chunk CRC mismatch: $type" }
            chunks += PngChunk(type, data)
            if (type == "IEND") break
        }
        return chunks
    }

    fun encode(chunks: List<PngChunk>): ByteArray {
        var total = SIGNATURE.size
        chunks.forEach { total += 12 + it.data.size }
        val out = java.io.ByteArrayOutputStream(total)
        out.write(SIGNATURE)
        chunks.forEach { chunk ->
            writeUInt32BE(out, chunk.data.size)
            out.write(chunk.type.toByteArray(Charsets.US_ASCII))
            out.write(chunk.data)
            writeUInt32BE(out, crc32(chunk.type.toByteArray(Charsets.US_ASCII) + chunk.data))
        }
        return out.toByteArray()
    }

    fun crc32(data: ByteArray): Int {
        val crc = java.util.zip.CRC32()
        crc.update(data)
        return crc.value.toInt()
    }

    private fun readUInt32BE(b: ByteArray, pos: Int): Int =
        ((b[pos].toInt() and 0xFF) shl 24) or
            ((b[pos + 1].toInt() and 0xFF) shl 16) or
            ((b[pos + 2].toInt() and 0xFF) shl 8) or
            (b[pos + 3].toInt() and 0xFF)

    private fun writeUInt32BE(out: java.io.ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }
}
