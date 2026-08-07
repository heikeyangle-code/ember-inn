package com.emberinn.engine.worldinfo

import java.io.File
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

/**
 * 向量库接口。query 对齐官方 multiQueryCollection：各集合先取 topK → 合并按分数降序 →
 * 过滤 threshold → 取全局 topK → 按 collectionId 分组返回。
 */
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

    /** 对齐官方 queryCollection：单集合 topK；hashes 不过滤阈值，metadata 按 score>=threshold 过滤。 */
    fun querySingle(
        collectionId: String,
        queryText: String,
        topK: Int,
        threshold: Double,
    ): VectorQueryResult
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

/** 本地离线嵌入：字符 + 二元组词袋（确定性、无需联网、语义较弱，适合无 API 时的本地 RAG 演示）。 */
class BagOfGramsEmbedding(
    private val dimensions: Int = 256,
) : EmbeddingProvider {

    override fun embed(texts: List<String>): List<List<Double>> = texts.map { text ->
        val vec = DoubleArray(dimensions)
        val grams = text.map { it.toString() } + text.windowed(2)
        for (g in grams) {
            val idx = Math.floorMod(g.hashCode(), dimensions)
            vec[idx] += 1.0
        }
        val norm = sqrt(vec.sumOf { it * it })
        vec.map { if (norm > 0.0) it / norm else 0.0 }
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
        // 对齐官方 multiQueryCollection：合并 → 分数降序 → 阈值 → 全局 topK → 分组
        val scored = collectionIds.flatMap { cid ->
            collections[cid].orEmpty().map { Triple(cid, it, cosine(queryVector, it.vector)) }
        }
            .filter { it.third >= threshold }
            .sortedByDescending { it.third }
            .take(topK)
        return scored.groupBy { it.first }.mapValues { (_, rows) ->
            VectorQueryResult(
                hashes = rows.map { it.second.hash },
                metadata = rows.map { (_, stored, score) ->
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

    override fun querySingle(
        collectionId: String,
        queryText: String,
        topK: Int,
        threshold: Double,
    ): VectorQueryResult {
        if (queryText.isBlank()) return VectorQueryResult(emptyList())
        val queryVector = embeddings.embed(listOf(queryText)).firstOrNull() ?: return VectorQueryResult(emptyList())
        val scored = collections[collectionId].orEmpty()
            .map { it to cosine(queryVector, it.vector) }
            .sortedByDescending { it.second }
            .take(topK)
        return VectorQueryResult(
            hashes = scored.map { it.first.hash },
            metadata = scored
                .filter { it.second >= threshold }
                .map { (stored, score) ->
                    buildJsonObject {
                        put("hash", JsonPrimitive(stored.hash))
                        put("text", JsonPrimitive(stored.text))
                        put("index", JsonPrimitive(stored.index))
                        put("score", JsonPrimitive(score))
                    }
                },
        )
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

/**
 * 磁盘持久化向量库（对齐官方 vectra.LocalIndex 落盘语义）：
 * 目录 `{root}/{source}/{collectionId}/{model}`，每集合一个 items.json：[{hash,text,index,vector}]。
 * insert 为 upsert（按 hash，对齐 vectra upsertItem）；query 全局 topK 语义见 VectorStore。
 * 重启不丢；内存库仅测试/临时用途。
 */
class FileVectorStore(
    private val rootDir: File,
    private val embeddings: EmbeddingProvider,
    private val source: String = "local",
    private val model: String = "",
) : VectorStore {

    private data class StoredItem(
        val hash: Long,
        val text: String,
        val index: Int,
        val vector: List<Double>,
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val cache = mutableMapOf<String, MutableList<StoredItem>>()

    private fun collectionDir(collectionId: String): File {
        val dir = File(rootDir, sanitize(source)).resolve(sanitize(collectionId)).resolve(sanitize(model))
        dir.mkdirs()
        return dir
    }

    private fun itemsFile(collectionId: String): File = File(collectionDir(collectionId), "items.json")

    private fun load(collectionId: String): MutableList<StoredItem> {
        cache[collectionId]?.let { return it }
        val file = itemsFile(collectionId)
        val list = if (file.exists()) {
            runCatching {
                val root = json.parseToJsonElement(file.readText()).jsonObject
                root["items"]?.jsonArray?.map { it.jsonObject }.orEmpty().map { obj ->
                    StoredItem(
                        hash = obj["hash"]!!.jsonPrimitive.content.toLong(),
                        text = obj["text"]!!.jsonPrimitive.content,
                        index = obj["index"]!!.jsonPrimitive.content.toInt(),
                        vector = obj["vector"]!!.jsonArray.mapNotNull { it.jsonPrimitive.content.toDoubleOrNull() },
                    )
                }.toMutableList()
            }.getOrDefault(mutableListOf())
        } else {
            mutableListOf()
        }
        cache[collectionId] = list
        return list
    }

    private fun save(collectionId: String) {
        val list = cache[collectionId] ?: return
        val root = buildJsonObject {
            put("items", JsonArray(list.map { item ->
                buildJsonObject {
                    put("hash", JsonPrimitive(item.hash))
                    put("text", JsonPrimitive(item.text))
                    put("index", JsonPrimitive(item.index))
                    put("vector", JsonArray(item.vector.map { JsonPrimitive(it) }))
                }
            }))
        }
        itemsFile(collectionId).writeText(json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), root))
    }

    override fun getSavedHashes(collectionId: String): Set<Long> =
        load(collectionId).map { it.hash }.toSet()

    override fun insert(collectionId: String, items: List<VectorItem>) {
        if (items.isEmpty()) return
        val list = load(collectionId)
        val vectors = embeddings.embed(items.map { it.text })
        items.zip(vectors).forEach { (item, vector) ->
            val existing = list.indexOfFirst { it.hash == item.hash }
            val stored = StoredItem(item.hash, item.text, item.index, vector)
            if (existing >= 0) list[existing] = stored else list += stored
        }
        save(collectionId)
    }

    override fun delete(collectionId: String, hashes: List<Long>) {
        if (hashes.isEmpty()) return
        val list = load(collectionId)
        val before = list.size
        list.removeAll { it.hash in hashes }
        if (list.size != before) save(collectionId)
    }

    override fun query(
        collectionIds: List<String>,
        queryText: String,
        topK: Int,
        threshold: Double,
    ): Map<String, VectorQueryResult> {
        if (collectionIds.isEmpty() || queryText.isBlank()) return emptyMap()
        val queryVector = embeddings.embed(listOf(queryText)).firstOrNull() ?: return emptyMap()
        val scored = collectionIds.flatMap { cid ->
            load(cid).map { Triple(cid, it, cosine(queryVector, it.vector)) }
        }
            .filter { it.third >= threshold }
            .sortedByDescending { it.third }
            .take(topK)
        return scored.groupBy { it.first }.mapValues { (_, rows) ->
            VectorQueryResult(
                hashes = rows.map { it.second.hash },
                metadata = rows.map { (_, stored, score) ->
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

    override fun querySingle(
        collectionId: String,
        queryText: String,
        topK: Int,
        threshold: Double,
    ): VectorQueryResult {
        if (queryText.isBlank()) return VectorQueryResult(emptyList())
        val queryVector = embeddings.embed(listOf(queryText)).firstOrNull() ?: return VectorQueryResult(emptyList())
        val scored = load(collectionId)
            .map { it to cosine(queryVector, it.vector) }
            .sortedByDescending { it.second }
            .take(topK)
        return VectorQueryResult(
            hashes = scored.map { it.first.hash },
            metadata = scored
                .filter { it.second >= threshold }
                .map { (stored, score) ->
                    buildJsonObject {
                        put("hash", JsonPrimitive(stored.hash))
                        put("text", JsonPrimitive(stored.text))
                        put("index", JsonPrimitive(stored.index))
                        put("score", JsonPrimitive(score))
                    }
                },
        )
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

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        return cleaned.ifEmpty { "_" }
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
