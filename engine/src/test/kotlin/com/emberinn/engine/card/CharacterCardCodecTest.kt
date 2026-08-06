package com.emberinn.engine.card

import com.emberinn.engine.card.png.PngChunkCodec
import com.emberinn.engine.card.png.PngChunk
import com.emberinn.engine.card.png.PngText
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCardCodecTest {

    @Test
    fun `write then read keeps json and adds both chunks before IEND`() {
        val json = """{"name":"测试","description":"1:1","spec":"chara_card_v3","spec_version":"3.0"}"""
        val png = CharacterCardCodec.writeToPng(minimalPng(), json)

        val chunks = PngChunkCodec.extract(png)
        val text = chunks.filter { it.type == "tEXt" }.map { PngText.decode(it.data) }
        assertEquals(2, text.size)
        assertEquals("chara", text[0].first)
        assertEquals("ccv3", text[1].first)
        assertEquals("IEND", chunks.last().type)

        val read = CharacterCardCodec.readFromPng(png)
        assertTrue(read.contains("\"name\":\"测试\""))
        val ccv3Json = String(Base64.getDecoder().decode(text[1].second), Charsets.UTF_8)
        assertTrue(ccv3Json.contains("\"spec\":\"chara_card_v3\""))
        assertTrue(ccv3Json.contains("\"spec_version\":\"3.0\""))
    }

    @Test
    fun `clean private fields matches official`() {
        val json = """{"name":"A","fav":true,"chat":[],"data":{"extensions":{"fav":true,"depth_prompt":{"p":"x"}}}}"""
        val cleaned = CharacterCardCodec.cleanPrivateFields(json)
        assertTrue(cleaned.contains("\"fav\":false"))
        assertTrue(cleaned.contains("\"data\":{\"extensions\":{\"fav\":false"))
        assertTrue(!cleaned.contains("\"chat\""))
    }

    private fun minimalPng(): ByteArray {
        return PngChunkCodec.encode(
            listOf(
                PngChunk("IHDR", ByteArray(13)),
                PngChunk("IDAT", byteArrayOf(1, 2, 3)),
                PngChunk("IEND", ByteArray(0)),
            ),
        )
    }
}
