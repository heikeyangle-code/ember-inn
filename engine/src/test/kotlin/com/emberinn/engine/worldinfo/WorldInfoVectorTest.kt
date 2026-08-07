package com.emberinn.engine.worldinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RAG/向量集成测试：对齐官方 vectors 扩展的同步、检索、强制激活注入。 */
class WorldInfoVectorTest {

    /** 确定性伪嵌入：显式关键词特征（篝火/余烬/森林/税收），语义直观、无碰撞。 */
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

    private fun entry(
        uid: Int,
        content: String,
        world: String = "w1",
        vectorized: Boolean = true,
        keys: List<String> = emptyList(),
    ) = WorldInfoEntry(
        world = world,
        uid = uid,
        order = 100 - uid,
        keys = keys,
        content = content,
        vectorized = vectorized,
        automationId = "auto_$uid",
    )

    @Test
    fun `synchronize inserts new entries and removes deleted ones`() {
        val store = InMemoryVectorStore(KeywordEmbedding())
        val a = entry(1, "篝火旁的余烬")
        val b = entry(2, "炉火边的故事")
        WorldInfoVectorActivation.synchronize(listOf(a, b), store)
        val cid = "world_" + StringHash.get("w1")
        assertEquals(setOf(StringHash.get("篝火旁的余烬"), StringHash.get("炉火边的故事")), store.getSavedHashes(cid))

        // b 被删除后重新同步 → 向量库删除对应 hash
        WorldInfoVectorActivation.synchronize(listOf(a), store)
        assertEquals(setOf(StringHash.get("篝火旁的余烬")), store.getSavedHashes(cid))
    }

    @Test
    fun `activate returns entries whose content is similar to chat`() {
        val store = InMemoryVectorStore(KeywordEmbedding())
        val a = entry(1, "篝火旁的余烬")
        val b = entry(2, "abcdefghijklmnopqrstuvwxyz unrelated")
        WorldInfoVectorActivation.synchronize(listOf(a, b), store)
        val activated = WorldInfoVectorActivation.activate(
            chat = listOf("今晚的篝火旁，余烬还在"),
            entries = listOf(a, b),
            store = store,
            settings = VectorSettings(enabled = true),
        )
        assertEquals(listOf(a), activated)
    }

    @Test
    fun `enabled for all allows non vectorized entries`() {
        val store = InMemoryVectorStore(KeywordEmbedding())
        val a = entry(1, "常驻知识：森林", vectorized = false)
        WorldInfoVectorActivation.synchronize(listOf(a), store, VectorSettings(enabledForAll = true))
        assertTrue(store.getSavedHashes("world_" + StringHash.get("w1")).isNotEmpty())
        val activated = WorldInfoVectorActivation.activate(
            chat = listOf("关于森林"),
            entries = listOf(a),
            store = store,
            settings = VectorSettings(enabled = true, enabledForAll = true),
        )
        assertEquals(listOf(a), activated)
    }

    @Test
    fun `vector activation forces entry into scanner without keywords`() {
        val store = InMemoryVectorStore(KeywordEmbedding())
        // 无关键词条目（keys 为空），正常扫描不会激活；RAG 强制激活后必须注入
        val a = entry(1, "篝火旁的余烬")
        val b = entry(2, "普通关键词条目", keys = listOf("不存在的词"))
        WorldInfoVectorActivation.synchronize(listOf(a, b), store)
        val activated = WorldInfoVectorActivation.activate(
            chat = listOf("今晚的篝火旁，余烬还在"),
            entries = listOf(a, b),
            store = store,
            settings = VectorSettings(enabled = true),
        )
        val external = activated.associateBy { it.world + "." + it.uid }
        val result = WorldInfoScanner().scan(
            chat = listOf("今晚的篝火旁，余烬还在"),
            maxContext = 1000,
            entries = listOf(a, b),
            externalActivations = external,
        )
        assertTrue(result.activated.any { it.uid == a.uid })
    }

    @Test
    fun `disabled vector extension activates nothing`() {
        val store = InMemoryVectorStore(KeywordEmbedding())
        val a = entry(1, "篝火旁的余烬")
        assertEquals(emptyList<WorldInfoEntry>(), WorldInfoVectorActivation.run(listOf("篝火"), listOf(a), store))
    }
}
