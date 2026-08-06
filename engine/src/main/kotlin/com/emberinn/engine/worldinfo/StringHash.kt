package com.emberinn.engine.worldinfo

/** 对齐官方 utils.js getStringHash（xmur3 风格 64 位字符串哈希）。 */
object StringHash {

    fun get(str: String, seed: Long = 0): Long {
        if (str.isEmpty()) return 0
        var h1 = 0xdeadbeefL xor seed
        var h2 = 0x41c6ce57L xor seed
        for (ch in str) {
            val c = ch.code.toLong()
            h1 = imul32(h1 xor c, 2654435761L)
            h2 = imul32(h2 xor c, 1597334677L)
        }
        h1 = imul32(h1 xor (h1 ushr 16), 2246822507L) xor imul32(h2 xor (h2 ushr 13), 3266489909L)
        h2 = imul32(h2 xor (h2 ushr 16), 2246822507L) xor imul32(h1 xor (h1 ushr 13), 3266489909L)
        return 4294967296L * (2097151L and h2) + (h1 and 0xFFFFFFFFL)
    }

    private fun imul32(a: Long, b: Long): Long =
        ((a and 0xFFFFFFFFL) * (b and 0xFFFFFFFFL)) and 0xFFFFFFFFL
}
