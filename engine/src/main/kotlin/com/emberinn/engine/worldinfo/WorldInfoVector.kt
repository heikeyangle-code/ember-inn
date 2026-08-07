package com.emberinn.engine.worldinfo

import kotlin.math.sqrt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 向量/世界书 RAG（对齐官方 extensions/vectors/index.js activateWorldInfo）：
 * - vectorized=true（或 enabledForAll）的条目按 world 分组同步进向量库
 * - 用最近 query 条聊天文本检索，按 score_threshold 过滤、max_entries 截断
 * - 命中的条目通过 WorldInfoBuffer.externalActivations 强制激活（跳过关键词/概率）
 */
data class VectorSettings(
    val enabled: Boolean = false,
    val enabledForAll: Boolean = false,
    val maxEntries: Int = 5,
    val scoreThreshold: Double = 0.25,
    val query: Int = 2,
    val source: String = "",
    val embeddingModel: String = "",
)

data class VectorItem(
    val hash: Long,
    val text: String,
    val index: Int,
)

data class VectorQueryResult(
    val hashes: List<Long>,
    val metadata: List<JsonObject> = emptyList(),
)

fun interface EmbeddingProvider {
    fun embed(texts: List<String>): List<List<Double>>
}

/** 向量库接口（对齐官方 /api/vector/query 返回）。 */
interface VectorStore {
    fun getSavedHashes(collectionId: String): Set<Long>
    fun insert(collectionId: String, items: List<VectorItem>)
    fun delete(collectionId: String, hashes: List<Long>)
    fun query(
        collectionIds: List<String>,
        queryText: String,
        topK: Int,
        threshold: Double,
    ): Map<String, VectorQueryResult>
}

/** OpenAI 兼容 /embeddings（官方 vectors 的 openai/other sources 通用协议）。 */
class OpenAiCompatibleEmbeddingProvider(
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : EmbeddingProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override fun embed(texts: List<String>): List<List<Double>> {
        val body = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("input", JsonArray(texts.map { JsonPrimitive(it) }))
        }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/embeddings")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            }
            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            return root["data"]?.jsonArray?.map { item ->
                item.jsonObject["embedding"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content.toDoubleOrNull() }.orEmpty()
            }.orEmpty()
        }
    }
}

/** 内存向量库（余弦相似度，>= threshold；用于无外部服务时的本地/测试路径）。 */
class InMemoryVectorStore(
    private val embeddings: EmbeddingProvider,
) : VectorStore {

    private data class Stored(val hash: Long, val text: String, val index: Int, val vector: List<Double>)

    private val collections = mutableMapOf<String, MutableList<Stored>>()

    override fun getSavedHashes(collectionId: String): Set<Long> =
        collections[collectionId]?.map { it.hash }?.toSet() ?: emptySet()

    override fun insert(collectionId: String, items: List<VectorItem>) {
        val vectors = embeddings.embed(items.map { it.text })
        val list = collections.getOrPut(collectionId) { mutableListOf() }
        items.zip(vectors).forEach { (item, vector) ->
            if (list.none { it.hash == item.hash }) {
                list += Stored(item.hash, item.text, item.index, vector)
            }
        }
    }

    override fun delete(collectionId: String, hashes: List<Long>) {
        collections[collectionId]?.removeAll { it.hash in hashes }
    }

    override fun query(
        collectionIds: List<String>,
        queryText: String,
        topK: Int,
        threshold: Double,
    ): Map<String, VectorQueryResult> {
        if (collectionIds.isEmpty() || queryText.isBlank()) return emptyMap()
        val queryVector = embeddings.embed(listOf(queryText)).firstOrNull() ?: return emptyMap()
        return collectionIds.associateWith { cid ->
            val scored = collections[cid].orEmpty()
                .map { it to cosine(queryVector, it.vector) }
                .filter { it.second >= threshold }
                .sortedByDescending { it.second }
                .take(topK)
            VectorQueryResult(
                hashes = scored.map { it.first.hash },
                metadata = scored.map { (stored, score) ->
                    buildJsonObject {
                        put("hash", JsonPrimitive(stored.hash))
                        put("text", JsonPrimitive(stored.text))
                        put("index", JsonPrimitive(stored.index))
                        put("score", JsonPrimitive(score))
                    }
                },
            )
        }
    }

    private fun cosine(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }
}

/** 对齐官方 activateWorldInfo：同步向量库 → 检索 → 返回应强制激活的条目。 */
object WorldInfoVectorActivation {

    fun synchronize(
        entries: List<WorldInfoEntry>,
        store: VectorStore,
        settings: VectorSettings = VectorSettings(),
    ) {
        val eligible = entries.filter {
            it.world.isNotEmpty() && !it.disable && it.content.isNotEmpty() &&
                (it.vectorized || settings.enabledForAll)
        }
        val grouped = eligible.groupBy { it.world }
        for ((world, group) in grouped) {
            val collectionId = "world_" + StringHash.get(world)
            val saved = store.getSavedHashes(collectionId)
            val current = group.map { StringHash.get(it.content) }
            val newItems = group
                .filter { StringHash.get(it.content) !in saved }
                .map { VectorItem(hash = StringHash.get(it.content), text = it.content, index = it.uid) }
            val deleted = saved.filter { it !in current }
            if (newItems.isNotEmpty()) store.insert(collectionId, newItems)
            if (deleted.isNotEmpty()) store.delete(collectionId, deleted)
        }
    }

    fun activate(
        chat: List<String>,
        entries: List<WorldInfoEntry>,
        store: VectorStore,
        settings: VectorSettings = VectorSettings(),
    ): List<WorldInfoEntry> {
        if (!settings.enabled) return emptyList()
        val eligible = entries.filter {
            it.world.isNotEmpty() && !it.disable && it.content.isNotEmpty() &&
                (it.vectorized || settings.enabledForAll)
        }
        val collectionIds = eligible.groupBy { it.world }.keys.map { "world_" + StringHash.get(it) }
        if (collectionIds.isEmpty()) return emptyList()

        // 对齐官方 getQueryText(chat, 'world-info')：最近 query 条非空消息，换行压缩后 trim
        val queryText = collapseNewlines(
            chat.filter { it.isNotBlank() }.takeLast(settings.query).joinToString("\n"),
        ).trim()
        if (queryText.isEmpty()) return emptyList()

        val results = store.query(collectionIds, queryText, settings.maxEntries, settings.scoreThreshold)
        val activatedHashes = results.values.flatMap { it.hashes }.distinct()
        if (activatedHashes.isEmpty()) return emptyList()
        return entries.filter { StringHash.get(it.content) in activatedHashes }
    }

    /** 一步完成：同步 + 检索（对齐官方 activateWorldInfo 主流程）。 */
    fun run(
        chat: List<String>,
        entries: List<WorldInfoEntry>,
        store: VectorStore,
        settings: VectorSettings = VectorSettings(),
    ): List<WorldInfoEntry> {
        if (!settings.enabled) return emptyList()
        synchronize(entries, store, settings)
        return activate(chat, entries, store, settings)
    }

    private fun collapseNewlines(text: String): String = text.replace(Regex("\\n+"), "\n")
}
