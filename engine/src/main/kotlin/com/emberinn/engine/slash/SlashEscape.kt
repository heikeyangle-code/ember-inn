package com.emberinn.engine.slash

/** 转义判定结果（对齐 SlashCommandParser.testSymbol）。 */
data class SlashEscapeResult(
    val found: Boolean,
    val index: Int,
    val jumpedEscapeSequence: Boolean,
)

/**
 * 对齐官方 SlashCommandParser.testSymbol / testSymbolLooseyGoosey：
 * STRICT_ESCAPING 下按反斜杠奇偶判定；loose 模式只认单反斜杠。
 */
object SlashEscape {

    fun testSymbol(
        text: String,
        index: Int,
        sequence: String,
        strict: Boolean,
        offset: Int = 0,
        jumpedEscapeSequence: Boolean = false,
    ): SlashEscapeResult {
        if (!strict) return loose(text, index, sequence, offset, jumpedEscapeSequence)
        return strict(text, index, sequence, offset, jumpedEscapeSequence)
    }

    private fun strict(
        text: String,
        index: Int,
        sequence: String,
        offset: Int,
        jumped: Boolean,
    ): SlashEscapeResult {
        var indexMut = index
        var jumpedMut = jumped
        val escapeOffset = if (jumpedMut) -1 else 0
        var escapes = 0
        val start = indexMut + offset + escapeOffset
        var i = start
        while (i < text.length && text[i] == '\\') {
            escapes++
            i++
        }
        val testFrom = start + escapes
        if (testFrom <= text.length && text.startsWith(sequence, testFrom)) {
            if (escapes == 0) return SlashEscapeResult(true, indexMut, jumpedMut)
            if (!jumpedMut && offset == 0) {
                indexMut++
                jumpedMut = true
            }
            return SlashEscapeResult(false, indexMut, jumpedMut)
        }
        return SlashEscapeResult(false, indexMut, jumpedMut)
    }

    private fun loose(
        text: String,
        index: Int,
        sequence: String,
        offset: Int,
        jumped: Boolean,
    ): SlashEscapeResult {
        var indexMut = index
        var jumpedMut = jumped
        val escapeOffset = if (jumpedMut) -1 else 0
        val charIdx = indexMut + offset + escapeOffset
        val escapes = if (charIdx in text.indices && text[charIdx] == '\\') 1 else 0
        val testFrom = charIdx + escapes
        if (testFrom <= text.length && text.startsWith(sequence, testFrom)) {
            if (escapes == 0) return SlashEscapeResult(true, indexMut, jumpedMut)
            if (!jumpedMut && offset == 0) {
                indexMut++
                jumpedMut = true
            }
            return SlashEscapeResult(false, indexMut, jumpedMut)
        }
        return SlashEscapeResult(false, indexMut, jumpedMut)
    }
}
