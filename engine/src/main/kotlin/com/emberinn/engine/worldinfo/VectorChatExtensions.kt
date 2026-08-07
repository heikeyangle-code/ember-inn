package com.emberinn.engine.worldinfo

import com.emberinn.engine.prompt.ExtensionPrompt
import kotlin.math.roundToInt
import kotlinx.serialization.json.jsonPrimitive

/**
 * 对齐官方 extensions/vectors/index.js：
 * - rearrangeChat（聊天历史向量重排）
 * - processFiles / ingestDataBankAttachments / injectDataBankChunks / retrieveFileChunks / vectorizeFile
 * - utils.js splitRecursive / trimToEndSentence / trimToStartSentence
 *
 * 边界（已标注）：
 * - substituteParams / substituteParamsExtended 由宏替换器注入（App 层接 MacroEngine），默认原样
 * - translate_files（翻译文件）与 summarize（聊天摘要）未实现（P3，官方默认关闭）
 * - trimToEndSentence 的 emoji 判定用 Unicode 码点近似（JS 用 \p{Emoji_Presentation}|\p{Extended_Pictographic}）
 */
data class VectorFileRef(
    val url: String,
    val name: String = "",
    val size: Long = 0,
    val text: String? = null,
)

data class VectorChatMessage(
    val name: String = "",
    val mes: String = "",
    val fileLength: Int = 0,
    val files: List<VectorFileRef> = emptyList(),
)

/** 向量扩展设置（对齐官方 settings 默认值）。 */
data class VectorChatSettings(
    val enabledChats: Boolean = false,
    val template: String = "Past events:\n{{text}}",
    val depth: Int = 2,
    val position: Int = 0, // extension_prompt_types.IN_PROMPT
    val protect: Int = 5,
    val insert: Int = 3,
    val query: Int = 2,
    val messageChunkSize: Int = 400,
    val scoreThreshold: Double = 0.25,
    val enabledFiles: Boolean = false,
    val sizeThreshold: Int = 10,
    val chunkSize: Int = 5000,
    val chunkCount: Int = 2,
    val overlapPercent: Int = 0,
    val onlyCustomBoundary: Boolean = false,
    val forceChunkDelimiter: String = "",
    val sizeThresholdDb: Int = 5,
    val chunkSizeDb: Int = 2500,
    val chunkCountDb: Int = 5,
    val overlapPercentDb: Int = 0,
    val fileTemplateDb: String = "Related information:\n{{text}}",
    val filePositionDb: Int = 0, // IN_PROMPT
    val fileDepthDb: Int = 4,
    val fileDepthRoleDb: Int = 0, // SYSTEM
    val includeWi: Boolean = false,
    val chatCollectionId: String = "chat",
    val macroSubstituter: (String) -> String = { it },
)

/** 一次向量拦截的结果：重排后的聊天 + 扩展提示 + 世界书强制激活条目。 */
data class VectorTransformResult(
    val newChat: List<VectorChatMessage>,
    val extensionPrompts: Map<String, ExtensionPrompt>,
    val worldInfoActivations: List<WorldInfoEntry>,
)

/** 对齐官方 getPromptPosition：BEFORE_PROMPT(2)→start、IN_PROMPT(0)→end、IN_CHAT(1)→in_chat。 */
fun mapExtensionPosition(position: Int): String? = when (position) {
    2 -> "start"
    0 -> "end"
    1 -> "in_chat"
    else -> null
}

fun mapExtensionRole(role: Int): String = when (role) {
    1 -> "user"
    2 -> "assistant"
    else -> "system"
}

object VectorTextUtils {

    /** 对齐 utils.js splitRecursive。 */
    fun splitRecursive(input: String, length: Int, delimiters: List<String> = listOf("\n\n", "\n", " ", "")): List<String> {
        if (length <= 0) return listOf(input)
        val delim = delimiters.firstOrNull() ?: ""
        val parts = if (delim.isEmpty()) input.map { it.toString() } else input.split(delim)
        val flatParts = parts.flatMap { p ->
            if (p.length < length) listOf(p) else splitRecursive(p, length, delimiters.drop(1))
        }

        val result = mutableListOf<String>()
        var i = 0
        while (i < flatParts.size) {
            var currentChunk = flatParts[i]
            var j = i + 1
            while (j < flatParts.size) {
                val nextChunk = flatParts[j]
                if (currentChunk.length + nextChunk.length + delim.length <= length) {
                    currentChunk += delim + nextChunk
                } else {
                    break
                }
                j++
            }
            i = j
            result += currentChunk
        }
        return result
    }

    /** 对齐 utils.js trimToEndSentence（emoji 用码点近似）。 */
    fun trimToEndSentence(input: String): String {
        if (input.isEmpty()) return ""
        val punctuation = setOf('.', '!', '?', '*', '"', ')', '}', '`', ']', '$', '。', '！', '？', '”', '）', '】', '’', '」', '_')
        val codePoints = input.codePoints().toArray()
        var last = -1
        for (i in codePoints.lastIndex downTo 0) {
            val cp = codePoints[i]
            val char = if (cp < 0x10000) cp.toChar() else null
            val emoji = isEmoji(cp)
            if ((char != null && char in punctuation) || emoji) {
                val prev = if (i > 0) codePoints[i - 1] else -1
                last = if (!emoji && i > 0 && (prev.toChar().isWhitespace() || prev == '\n'.code)) {
                    i - 1
                } else {
                    i
                }
                break
            }
        }
        if (last == -1) return input.trimEnd()
        return String(codePoints, 0, last + 1).trimEnd()
    }

    /** 对齐 utils.js trimToStartSentence。 */
    fun trimToStartSentence(input: String): String {
        if (input.isEmpty()) return ""
        var p1 = input.indexOf('.')
        var p2 = input.indexOf('!')
        var p3 = input.indexOf('?')
        val p4 = input.indexOf('\n')
        var first = p1
        var skip1 = false
        if (p2 > 0 && p2 < first) first = p2
        if (p3 > 0 && p3 < first) first = p3
        if (p4 > 0 && p4 < first) { first = p4; skip1 = true }
        if (first > 0) {
            // JS substring 会自动钳制到字符串长度，Kotlin 需要显式 coerce
            return if (skip1) {
                input.substring((first + 1).coerceAtMost(input.length))
            } else {
                input.substring((first + 2).coerceAtMost(input.length))
            }
        }
        return input
    }

    /** 对齐 vectors overlapChunks。 */
    fun overlapChunks(chunk: String, index: Int, chunks: List<String>, overlapSize: Int): String {
        val halfOverlap = overlapSize / 2
        val nextOverlap = chunks.getOrNull(index + 1)?.take(halfOverlap)?.let { trimToEndSentence(it) }.orEmpty()
        val prevOverlap = chunks.getOrNull(index - 1)?.takeLast(halfOverlap)?.let { trimToStartSentence(it) }.orEmpty()
        return listOf(prevOverlap, chunk, nextOverlap).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** 对齐 power-user.js collapseNewlines。 */
    fun collapseNewlines(text: String): String = text.replace(Regex("\\n+"), "\n")

    /** 保持顺序去重（对齐官方 onlyUnique）。 */
    fun <T> onlyUnique(items: List<T>): List<T> = items.distinct()

    private fun isEmoji(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF ||
            cp in 0xFE00..0xFE0F || cp in 0x1F1E6..0x1F1FF
}

/** 对齐官方 getFileCollectionId。 */
fun getFileCollectionId(fileUrl: String): String = "file_" + StringHash.get(fileUrl)

/** 对齐官方 getQueryText（不含 summarize：P3）。 */
fun vectorQueryText(
    chat: List<VectorChatMessage>,
    settings: VectorChatSettings,
): String {
    val texts = chat.map { message ->
        val fileLength = message.fileLength.coerceAtLeast(0)
        val raw = message.mes.takeLast((message.mes.length - fileLength).coerceAtLeast(0))
        settings.macroSubstituter(raw).trim()
    }
        .filter { it.isNotEmpty() }
        .reversed()
        .take(settings.query)
    val queryText = texts.joinToString("\n")
    return VectorTextUtils.collapseNewlines(queryText).trim()
}

/** 对齐官方 getPromptText（{{text}} 模板替换）。 */
fun renderVectorTemplate(template: String, text: String): String =
    template.replace("{{text}}", text)

/**
 * 对齐官方 rearrangeChat + processFiles + activateWorldInfo：
 * 输入原聊天与向量库，返回重排后的聊天、扩展提示（3_vectors / 4_vectors_data_bank）与世界书激活条目。
 */
object VectorChatRearranger {

    private const val TAG_MEMORY = "3_vectors"
    private const val TAG_DATA_BANK = "4_vectors_data_bank"

    fun rearrange(
        chat: List<VectorChatMessage>,
        store: VectorStore,
        settings: VectorChatSettings = VectorChatSettings(),
        worldInfoEntries: List<WorldInfoEntry> = emptyList(),
        worldInfoSettings: VectorSettings = VectorSettings(),
        dataBankFiles: List<VectorFileRef> = emptyList(),
        fileTextResolver: (String) -> String? = { null },
    ): VectorTransformResult {
        val newChat = chat.toMutableList()
        val prompts = mutableMapOf<String, ExtensionPrompt>()

        // 官方 rearrangeChat 先清空两个扩展提示
        // （返回空 content 的提示等价于清除，组装管线只注入非空内容）

        if (settings.enabledFiles) {
            processFiles(newChat, store, settings, dataBankFiles, fileTextResolver, prompts)
        }

        val worldInfoActivations = if (worldInfoEntries.isNotEmpty()) {
            WorldInfoVectorActivation.run(
                chat = newChat.map { it.mes },
                entries = worldInfoEntries,
                store = store,
                settings = worldInfoSettings,
            )
        } else {
            emptyList()
        }

        if (settings.enabledChats) {
            rearrangeChat(newChat, store, settings, prompts)
        }

        return VectorTransformResult(
            newChat = newChat,
            extensionPrompts = prompts,
            worldInfoActivations = worldInfoActivations,
        )
    }

    private fun rearrangeChat(
        chat: MutableList<VectorChatMessage>,
        store: VectorStore,
        settings: VectorChatSettings,
        prompts: MutableMap<String, ExtensionPrompt>,
    ) {
        if (chat.size < settings.protect) return
        val queryText = vectorQueryText(chat, settings)
        if (queryText.isEmpty()) return

        val queryResults = store.querySingle(
            settings.chatCollectionId,
            queryText,
            settings.insert,
            settings.scoreThreshold,
        )
        val queryHashes = VectorTextUtils.onlyUnique(queryResults.hashes)
        if (queryHashes.isEmpty()) return

        val queriedMessages = mutableListOf<VectorChatMessage>()
        val insertedHashes = mutableSetOf<Long>()
        val retainMessages = chat.takeLast(settings.protect)

        for (message in chat) {
            if (message in retainMessages || message.mes.isEmpty()) continue
            val hash = StringHash.get(settings.macroSubstituter(message.mes))
            if (hash in queryHashes && hash !in insertedHashes) {
                queriedMessages += message
                insertedHashes += hash
            }
        }

        // 对齐官方：按查询结果顺序排列（结果中更相关的在低位）
        queriedMessages.sortBy { queryHashes.indexOf(StringHash.get(settings.macroSubstituter(it.mes))) }

        chat.removeAll(queriedMessages.toSet())
        if (queriedMessages.isEmpty()) return

        val insertedText = renderVectorTemplate(
            settings.template,
            VectorTextUtils.collapseNewlines(
                queriedMessages.joinToString("\n\n") { "${it.name}: ${it.mes}".trim() },
            ).trim(),
        )
        mapExtensionPosition(settings.position)?.let { position ->
            prompts[TAG_MEMORY] = ExtensionPrompt(
                identifier = TAG_MEMORY,
                role = "system",
                content = insertedText,
                position = position,
                depth = settings.depth,
            )
        }
    }

    private fun processFiles(
        chat: MutableList<VectorChatMessage>,
        store: VectorStore,
        settings: VectorChatSettings,
        dataBankFiles: List<VectorFileRef>,
        fileTextResolver: (String) -> String?,
        prompts: MutableMap<String, ExtensionPrompt>,
    ) {
        val dataBankCollectionIds = ingestDataBankAttachments(dataBankFiles, store, settings, fileTextResolver)
        if (dataBankCollectionIds.isNotEmpty()) {
            val queryText = vectorQueryText(chat, settings)
            if (queryText.isNotEmpty()) {
                injectDataBankChunks(queryText, dataBankCollectionIds, store, settings, prompts)
            }
        }

        for (message in chat) {
            if (message.files.isEmpty()) continue
            // 官方：文件内容占据 mes 前 fileLength 字符
            val allFileText = message.mes.take(message.fileLength).trim()
            val thresholdLength = settings.sizeThreshold * 1024
            if (allFileText.length < thresholdLength) continue

            val queryText = vectorQueryText(chat, settings)
            val allFileChunks = mutableListOf<String>()

            for (file in message.files) {
                val collectionId = getFileCollectionId(file.url)
                val saved = store.getSavedHashes(collectionId)
                if (saved.isEmpty()) {
                    val fileText = file.text ?: fileTextResolver(file.url)
                    if (fileText.isNullOrEmpty()) continue
                    vectorizeFile(fileText, file.name, collectionId, store, settings)
                }
                val fileChunks = retrieveFileChunks(queryText, collectionId, store, settings)
                if (fileChunks.isNotEmpty()) allFileChunks += fileChunks
            }

            if (allFileChunks.isNotEmpty()) {
                val rest = message.mes.substring(message.fileLength)
                chat[chat.indexOf(message)] = message.copy(mes = allFileChunks.joinToString("\n\n") + "\n\n" + rest)
            }
        }
    }

    private fun ingestDataBankAttachments(
        files: List<VectorFileRef>,
        store: VectorStore,
        settings: VectorChatSettings,
        fileTextResolver: (String) -> String?,
    ): List<String> {
        val collectionIds = mutableListOf<String>()
        for (file in files) {
            val collectionId = getFileCollectionId(file.url)
            collectionIds += collectionId
            if (store.getSavedHashes(collectionId).isNotEmpty()) continue
            val fileText = file.text ?: fileTextResolver(file.url) ?: continue
            val thresholdLength = settings.sizeThresholdDb * 1024
            val chunkSize = if (file.size > thresholdLength) settings.chunkSizeDb else -1
            vectorizeFile(fileText, file.name, collectionId, store, settings.copy(chunkSize = chunkSize), overlapPercent = settings.overlapPercentDb)
        }
        return collectionIds
    }

    private fun injectDataBankChunks(
        queryText: String,
        collectionIds: List<String>,
        store: VectorStore,
        settings: VectorChatSettings,
        prompts: MutableMap<String, ExtensionPrompt>,
    ) {
        val queryResults = store.query(collectionIds, queryText, settings.chunkCountDb, settings.scoreThreshold)
        val textResult = StringBuilder()
        // 对齐官方：只遍历有结果的集合；metadata 按 index 升序、取 text、去重
        for (collectionId in queryResults.keys) {
            val texts = queryResults[collectionId]?.metadata.orEmpty()
                .mapNotNull { meta ->
                    val index = meta["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val text = meta["text"]?.jsonPrimitive?.content.orEmpty()
                    if (text.isEmpty()) null else index to text
                }
                .sortedBy { it.first }
                .map { it.second }
                .distinct()
            textResult.append(texts.joinToString("\n")).append("\n\n")
        }
        if (textResult.isEmpty()) return
        val insertedText = renderVectorTemplate(settings.fileTemplateDb, textResult.toString())
        mapExtensionPosition(settings.filePositionDb)?.let { position ->
            prompts[TAG_DATA_BANK] = ExtensionPrompt(
                identifier = TAG_DATA_BANK,
                role = mapExtensionRole(settings.fileDepthRoleDb),
                content = insertedText,
                position = position,
                depth = settings.fileDepthDb,
            )
        }
    }

    private fun retrieveFileChunks(
        queryText: String,
        collectionId: String,
        store: VectorStore,
        settings: VectorChatSettings,
    ): String {
        val queryResults = store.querySingle(collectionId, queryText, settings.chunkCount, settings.scoreThreshold)
        val texts = queryResults.metadata
            .mapNotNull { meta ->
                val index = meta["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val text = meta["text"]?.jsonPrimitive?.content.orEmpty()
                if (text.isEmpty()) null else index to text
            }
            .sortedBy { it.first }
            .map { it.second }
            .distinct()
        return texts.joinToString("\n")
    }

    internal fun vectorizeFile(
        fileText: String,
        fileName: String,
        collectionId: String,
        store: VectorStore,
        settings: VectorChatSettings,
        overlapPercent: Int = settings.overlapPercent,
    ) {
        var text = fileText
        val overlapSize = (settings.chunkSize * overlapPercent / 100.0).roundToInt()
        var chunkSize = settings.chunkSize
        if (overlapSize > 0) chunkSize -= overlapSize
        val delimiters = getChunkDelimiters(settings)
        val chunks = if (settings.onlyCustomBoundary && settings.forceChunkDelimiter.isNotEmpty()) {
            val byDelimiter = text.split(settings.forceChunkDelimiter)
            byDelimiter.mapIndexed { i, c ->
                if (overlapSize > 0) VectorTextUtils.overlapChunks(c, i, byDelimiter, overlapSize) else c
            }
        } else {
            val split = VectorTextUtils.splitRecursive(text, chunkSize, delimiters)
            split.mapIndexed { i, c ->
                if (overlapSize > 0) VectorTextUtils.overlapChunks(c, i, split, overlapSize) else c
            }
        }
        val items = chunks.mapIndexed { index, chunk ->
            VectorItem(hash = StringHash.get(chunk), text = chunk, index = index)
        }
        store.insert(collectionId, items)
    }

    private fun getChunkDelimiters(settings: VectorChatSettings): List<String> {
        val delimiters = mutableListOf("\n\n", "\n", " ", "")
        if (settings.forceChunkDelimiter.isNotEmpty()) delimiters.add(0, settings.forceChunkDelimiter)
        return delimiters
    }
}
