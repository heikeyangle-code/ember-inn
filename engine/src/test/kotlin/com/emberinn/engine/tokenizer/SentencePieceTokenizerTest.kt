package com.emberinn.engine.tokenizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SentencePiece BPE 引擎：确定性、已知 Llama 编码、字节往返（官方库缺失，差分待有库环境，登记）。 */
class SentencePieceTokenizerTest {

    @Test
    fun `llama hello matches known token ids`() {
        val tok = SentencePieceTokenizer.forResource("llama.model")
        // Llama-2 已知编码（带 dummy prefix）："▁Hello" = 15043
        assertEquals(listOf(15043), tok.encode("Hello"))
        assertEquals("Hello", tok.decode(tok.encode("Hello")))
    }

    @Test
    fun `gemma deterministic and chinese roundtrip`() {
        val tok = SentencePieceTokenizer.forResource("gemma.model")
        val ids = tok.encode("你好，世界")
        assertTrue(ids.isNotEmpty())
        assertEquals(ids, tok.encode("你好，世界"))
        assertEquals("你好，世界", tok.decode(ids))
    }

    @Test
    fun `mistral roundtrips ascii`() {
        val tok = SentencePieceTokenizer.forResource("mistral.model")
        assertEquals("Hello world", tok.decode(tok.encode("Hello world")))
    }

    @Test
    fun `byte fallback roundtrips control char`() {
        val tok = SentencePieceTokenizer.forResource("llama.model")
        val text = "a\u0001b"
        assertEquals(text, tok.decode(tok.encode(text)))
    }
}
