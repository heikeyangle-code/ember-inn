package com.emberinn.engine.tokenizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SentencePiece BPE 引擎：确定性、已知 Llama 编码、字节往返（官方库缺失，差分待有库环境，登记）。 */
class SentencePieceTokenizerTest {

    @Test
    fun `gemma deterministic and chinese roundtrip`() {
        val tok = SentencePieceTokenizer.forResource("gemma.model")
        val ids = tok.encode("你好，世界")
        assertTrue(ids.isNotEmpty())
        assertEquals(ids, tok.encode("你好，世界"))
        assertEquals("你好，世界", tok.decode(ids))
    }

}
