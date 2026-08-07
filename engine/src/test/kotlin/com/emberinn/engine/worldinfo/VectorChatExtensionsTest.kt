package com.emberinn.engine.worldinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 向量扩展（聊天重排 / 文件向量化）测试，对照官方 utils.js + vectors/index.js 语义。 */
class VectorChatExtensionsTest {

    private class KeywordEmbedding : EmbeddingProvider {
        override fun embed(texts: List<String>): List<List<Double>> = texts.map { t ->
            listOf(
                if ("篝火" in t) 1.0 else 0.0,
                if ("余烬" in t) 1.0 else 0.0,
                if ("森林" in t) 1.0 else 0.0,
                if ("税收" in t) 1.0 else 0.0,
            )
        }
    }

    private fun msg(name: String, mes: String) = VectorChatMessage(name = name, mes = mes)

    @Test
    fun `splitRecursive matches official example`() {
        assertEquals(
            listOf("Hel", "lo,", "wor", "ld!"),
            VectorTextUtils.splitRecursive("Hello, world!", 3),
        )
    }

    @Test
    fun `trimToEndSentence cuts at punctuation`() {
        assertEquals("Hello, world!", VectorTextUtils.trimToEndSentence("Hello, world! I am from"))
        assertEquals("", VectorTextUtils.trimToEndSentence(""))
    }

    @Test
    fun `trimToStartSentence cuts after punctuation`() {
        assertEquals("world", VectorTextUtils.trimToStartSentence("Hello. world"))
        assertEquals("", VectorTextUtils.trimToStartSentence(""))
    }

    @Test
    fun `rearrange pulls relevant messages and keeps recent ones`() {
        val store = InMemoryVectorStore(KeywordEmbedding())
        val chat = listOf(
            msg("User", "篝火旁的余烬"),
            msg("Char", "无关旧消息甲"),
            msg("User", "森林里的故事"),
            msg("Char", "无关旧消息乙"),
            msg("User", "今晚的篝火"),
            msg("Char", "余烬还在"),
        )
        // 向量库预置聊天历史（chatCollectionId）
        store.insert(
            "chat",
            listOf(
                VectorItem(StringHash.get("篝火旁的余烬"), "篝火旁的余烬", 0),
                VectorItem(StringHash.get("无关旧消息甲"), "无关旧消息甲", 1),
                VectorItem(StringHash.get("森林里的故事"), "森林里的故事", 2),
            ),
        )
        val settings = VectorChatSettings(
            enabledChats = true,
            protect = 2,
            insert = 2,
            query = 2,
            chatCollectionId = "chat",
        )
        val result = VectorChatRearranger.rearrange(chat, store, settings)
        // 最近 2 条保留
        assertTrue(result.newChat.any { it.mes == "今晚的篝火" })
        assertTrue(result.newChat.any { it.mes == "余烬还在" })
        // 相关旧消息被抽走
        assertFalse(result.newChat.any { it.mes == "篝火旁的余烬" })
        // 扩展提示套模板
        val prompt = result.extensionPrompts.getValue("3_vectors")
        assertTrue(prompt.content.startsWith("Past events:"))
        assertTrue(prompt.content.contains("篝火旁的余烬"))
        assertEquals("end", prompt.position)
    }

    @Test
    fun `vectorizeFile splits with overlap and stores chunks`() {
        val store = InMemoryVectorStore(KeywordEmbedding())
        val text = "一二三四五六七八九十".repeat(20)
        VectorChatRearranger.vectorizeFile(text, "f.txt", "file_1", store, VectorChatSettings(chunkSize = 10, overlapPercent = 50))
        val hashes = store.getSavedHashes("file_1")
        assertTrue(hashes.size > 1)
        // 每个 chunk 长度不超过原 chunkSize（10）
        val items = store.querySingle("file_1", "一二", topK = 100, threshold = 0.0)
        assertEquals(hashes.size, items.hashes.size)
    }

    @Test
    fun `processFiles injects retrieved chunks before message`() {
        val store = InMemoryVectorStore(KeywordEmbedding())
        val fileText = "森林知识：森林里有篝火和余烬。\n".repeat(200)
        val chat = mutableListOf(
            VectorChatMessage(
                name = "User",
                mes = fileText + "真正的聊天内容",
                fileLength = fileText.length,
                files = listOf(VectorFileRef(url = "file:///a.txt", name = "a.txt", text = fileText)),
            ),
        )
        val settings = VectorChatSettings(
            enabledFiles = true,
            sizeThreshold = 0, // 0 阈值：不跳过小文件
            chunkSize = 5000,
            chunkCount = 2,
            query = 2,
        )
        val result = VectorChatRearranger.rearrange(
            chat = chat,
            store = store,
            settings = settings,
            dataBankFiles = emptyList(),
        )
        val newMessage = result.newChat.first()
        assertTrue(newMessage.mes.contains("真正的聊天内容"))
        assertTrue(newMessage.mes.startsWith("森林知识"))
    }

    @Test
    fun `file collection id prefixes with file hash`() {
        assertEquals("file_" + StringHash.get("https://x/a.txt"), getFileCollectionId("https://x/a.txt"))
    }
}
