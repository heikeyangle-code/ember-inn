package com.emberinn.engine.tokenizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Claude/Llama3 HF BPE 引擎：确定性、added token、字节往返（官方库缺失，差分待有库环境，登记）。 */
class HfBpeTokenizerTest {

    @Test
    fun `claude tokenizes deterministically and roundtrips ascii`() {
        val tok = HfBpeTokenizer.forResource("claude.json")
        val ids = tok.encode("Hello, world!")
        assertTrue(ids.isNotEmpty())
        assertEquals(ids, tok.encode("Hello, world!"))
        assertEquals("Hello, world!", tok.decode(ids))
    }

    @Test
    fun `claude special token maps to id 0`() {
        val tok = HfBpeTokenizer.forResource("claude.json")
        assertEquals(listOf(0), tok.encode("<EOT>"))
    }

    @Test
    fun `claude counts chinese text`() {
        val tok = HfBpeTokenizer.forResource("claude.json")
        assertTrue(tok.count("你好，世界") > 0)
    }

}
