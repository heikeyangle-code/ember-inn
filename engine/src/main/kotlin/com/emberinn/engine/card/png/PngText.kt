package com.emberinn.engine.card.png

/**
 * tEXt chunk 编解码（对齐官方 png-chunk-text）：
 * keyword (Latin-1) + 0x00 + text (Latin-1)。
 */
object PngText {

    fun encode(keyword: String, text: String): ByteArray {
        val kw = keyword.toByteArray(Charsets.ISO_8859_1)
        val tx = text.toByteArray(Charsets.ISO_8859_1)
        return kw + byteArrayOf(0) + tx
    }

    fun decode(data: ByteArray): Pair<String, String> {
        val sep = data.indexOf(0)
        require(sep > 0) { "Invalid tEXt chunk" }
        val keyword = String(data, 0, sep, Charsets.ISO_8859_1)
        val text = String(data, sep + 1, data.size - sep - 1, Charsets.ISO_8859_1)
        return keyword to text
    }
}
