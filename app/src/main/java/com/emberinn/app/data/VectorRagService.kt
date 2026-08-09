package com.emberinn.app.data

import android.content.Context
import com.emberinn.app.ui.settings.ServicesPrefs
import com.emberinn.engine.worldinfo.BagOfGramsEmbedding
import com.emberinn.engine.worldinfo.FileVectorStore
import com.emberinn.engine.worldinfo.OpenAiCompatibleEmbeddingProvider
import com.emberinn.engine.worldinfo.VectorChatSettings
import com.emberinn.engine.worldinfo.VectorFileRef
import com.emberinn.engine.worldinfo.VectorSettings
import com.emberinn.engine.worldinfo.VectorStore
import java.io.File

/**
 * 向量 RAG App 接线（官方 extensions/vectors 扩展语义）：
 * - 嵌入来源：OpenAI 兼容 /embeddings（设置里选 openai）或本地 BagOfGram（local）
 * - 向量库：FileVectorStore 磁盘持久化（对齐官方 vectra 目录 root/source/collection/model/items.json）
 * - 数据银行：filesDir/databank/ 下的文本文件，发送时按官方 vectors 分块检索注入
 * 边界：translate_files / summarize 官方默认关闭，未做（见 HANDOFF 第 8 节）。
 */
class VectorRagService(context: Context) {

    private val appContext = context.applicationContext

    fun enabled(): Boolean = ServicesPrefs.vectorEnabled(appContext)

    /** OpenAI 兼容嵌入已开但配置不完整（缺地址/Key/模型）——本轮禁用并提示。 */
    fun configIncomplete(): Boolean {
        if (!enabled()) return false
        if (ServicesPrefs.vectorProvider(appContext) != "openai") return false
        return ServicesPrefs.vectorUrl(appContext).isBlank() ||
            ServicesPrefs.vectorApiKey(appContext).isBlank() ||
            ServicesPrefs.vectorModel(appContext).isBlank()
    }

    fun chatSettings(): VectorChatSettings = VectorChatSettings(
        enabledChats = ServicesPrefs.vectorEnabledChats(appContext),
        enabledFiles = ServicesPrefs.vectorEnabledFiles(appContext),
        query = ServicesPrefs.vectorQuery(appContext),
        insert = ServicesPrefs.vectorInsert(appContext),
        protect = ServicesPrefs.vectorProtect(appContext),
        scoreThreshold = ServicesPrefs.vectorThreshold(appContext),
    )

    fun worldSettings(): VectorSettings = VectorSettings(
        enabled = enabled(),
        maxEntries = ServicesPrefs.vectorInsert(appContext),
        scoreThreshold = ServicesPrefs.vectorThreshold(appContext),
        query = ServicesPrefs.vectorQuery(appContext),
    )

    /** 向量库实例（每次发送重建以读取最新设置；磁盘 items.json 持久化，重启不丢）。 */
    fun store(): VectorStore? {
        if (!enabled()) return null
        val provider = ServicesPrefs.vectorProvider(appContext)
        return when (provider) {
            "openai" -> {
                val url = ServicesPrefs.vectorUrl(appContext).trim()
                val key = ServicesPrefs.vectorApiKey(appContext).trim()
                val model = ServicesPrefs.vectorModel(appContext).trim()
                if (url.isEmpty() || key.isEmpty() || model.isEmpty()) return null
                FileVectorStore(
                    rootDir = File(appContext.filesDir, "vector"),
                    embeddings = OpenAiCompatibleEmbeddingProvider(baseUrl = url, apiKey = key, model = model),
                    source = "app",
                    model = model,
                )
            }
            else -> FileVectorStore(
                rootDir = File(appContext.filesDir, "vector"),
                embeddings = BagOfGramsEmbedding(),
                source = "app",
                model = "bag-of-grams",
            )
        }
    }

    // ---- 数据银行（官方 Data Bank 附件；本 App 存 filesDir/databank/）----

    fun dataBankNames(): List<String> =
        dataBankDir().listFiles()?.map { it.name }?.sorted() ?: emptyList()

    fun dataBankFiles(): List<VectorFileRef> = dataBankNames().map { name ->
        val f = File(dataBankDir(), name)
        VectorFileRef(url = f.absolutePath, name = name, size = f.length())
    }

    fun readDataBankText(path: String): String? = runCatching {
        File(path).readText(Charsets.UTF_8)
    }.getOrNull()

    fun saveDataBankFile(name: String, bytes: ByteArray): Boolean = runCatching {
        val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "data-${System.currentTimeMillis()}.txt" }
        val f = File(dataBankDir(), safe)
        f.writeBytes(bytes)
        true
    }.getOrDefault(false)

    fun deleteDataBankFile(name: String) {
        val f = File(dataBankDir(), name)
        if (f.isFile) f.delete()
    }

    private fun dataBankDir(): File = File(appContext.filesDir, "databank").apply { mkdirs() }
}
