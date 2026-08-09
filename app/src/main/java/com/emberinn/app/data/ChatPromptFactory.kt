package com.emberinn.app.data

import com.emberinn.engine.macros.ChatMessage
import com.emberinn.engine.macros.CharacterFields
import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.macros.SystemFields
import com.emberinn.engine.media.MediaAttachment
import com.emberinn.engine.media.MediaDisplay
import com.emberinn.engine.prompt.CharacterCardFieldsEngine
import com.emberinn.engine.prompt.CharacterCardSource
import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.prompt.PromptAssembler
import com.emberinn.engine.prompt.PromptPipeline
import com.emberinn.engine.prompt.PromptUtils
import com.emberinn.engine.worldinfo.GlobalScanData
import com.emberinn.engine.worldinfo.TokenCounterFactory
import com.emberinn.engine.worldinfo.WorldBookEntryParser
import com.emberinn.engine.worldinfo.WorldInfoEntry
import com.emberinn.engine.worldinfo.WorldInfoScanner
import com.emberinn.engine.worldinfo.WorldInfoSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 提示词工厂：角色卡 + 聊天历史 → PromptPipeline 总装 → 最终 CompletionMessage。
 * App 发消息唯一入口（世界书/人设/作者注释/示例/历史全部在引擎内完成）。
 */
class ChatPromptFactory {

    /** 对齐官方 openai.js default_impersonation_prompt。 */
    companion object {
        const val DEFAULT_IMPERSONATION_PROMPT =
            "[Write your next reply from the point of view of {{user}}, using the chat history so far as a guideline for the writing style of {{user}}. Don't write as {{char}} or system. Don't describe actions of {{char}}.]"
    }

    private val json = Json { ignoreUnknownKeys = true }

    data class Prepared(
        val messages: List<CompletionMessage>,
        val activatedWorldInfo: List<WorldInfoEntry>,
        val counts: Map<String, Int> = emptyMap(),
        val maxContextTokens: Int = 8192,
    )

    fun prepare(
        characterRawJson: String?,
        history: List<JsonElement>,
        userName: String,
        charName: String,
        model: String,
        maxContextTokens: Int = 8192,
        maxTokens: Int = 512,
        type: String = "generate",
        continuePrefill: Boolean = false,
        impersonationPrompt: String = DEFAULT_IMPERSONATION_PROMPT,
        cyclePrompt: String = "",
        imageInlining: Boolean = false,
        videoInlining: Boolean = false,
        audioInlining: Boolean = false,
    ): Prepared {
        val parsed = characterRawJson?.let { runCatching { parseCard(it) }.getOrNull() }
        val fields = CharacterCardFieldsEngine.fields(parsed?.source)
        // 对齐官方 MacroEnvBuilder：character 字段来自 getCharacterCardFields（已 baseChatReplace）
        val env = MacroEnv(
            user = userName,
            char = charName,
            character = CharacterFields(
                charPrompt = fields.system,
                charInstruction = fields.jailbreak,
                description = fields.description,
                personality = fields.personality,
                scenario = fields.scenario,
                persona = fields.persona,
                mesExamplesRaw = fields.mesExamples,
                charDepthPrompt = fields.charDepthPrompt,
                creatorNotes = fields.creatorNotes,
                firstMessage = fields.firstMessage,
                alternateGreetings = fields.alternateGreetings,
                version = fields.version,
            ),
            system = SystemFields(model = model),
        )
        val tokenCounter = TokenCounterFactory.forModel(model)

        // 历史消息（JSONL → 引擎 ChatMessage → PromptMessage）
        val chatMessages = history.mapNotNull { el ->
            val obj = el.jsonObject
            val isUser = obj["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true
            val mes = obj["mes"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ChatMessage(
                mes = mes,
                isUser = isUser,
                name = obj["name"]?.jsonPrimitive?.contentOrNull,
            )
        }
        // 对齐官方 setOpenAIMessages：进总装前消息是“新的在前”（official messages[i] 反向填充）；
        // 总装内部 populationInjectionPrompts 会 reverse 一次、历史填充再 reverse 一次。
        // 之前传“旧的在前”导致 continue_prefill 把最老消息当续写对象。
        val historyMessages = PromptAssembler.toOpenAiMessages(chatMessages, user = userName)
            .mapIndexed { i, pm ->
                val el = history.getOrNull(i)?.jsonObject
                val extra = el?.get("extra") as? JsonObject
                pm.copy(
                    identifier = "chatHistory",
                    media = extra?.get("media")?.jsonArray?.mapNotNull { me ->
                        val mo = me.jsonObject
                        val rawUrl = mo["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        // 官方存储路径、请求时才 fetch→base64；本地文件直接读成 data URL 再进链内联
                        val url = if (rawUrl.startsWith("data:")) {
                            rawUrl
                        } else {
                            val f = java.io.File(rawUrl)
                            if (!f.exists()) return@mapNotNull null
                            val mime = mimeFromPath(rawUrl)
                            "data:$mime;base64," + java.util.Base64.getEncoder().encodeToString(f.readBytes())
                        }
                        MediaAttachment(
                            type = mo["type"]?.jsonPrimitive?.contentOrNull?.ifBlank { "image" } ?: "image",
                            url = url,
                            title = mo["title"]?.jsonPrimitive?.contentOrNull ?: "",
                        )
                    },
                    mediaDisplay = extra?.get("media_display")?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it == MediaDisplay.LIST || it == MediaDisplay.GALLERY },
                    mediaIndex = extra?.get("media_index")?.jsonPrimitive?.content?.toIntOrNull(),
                )
            }
            .asReversed()

        // 世界书扫描（角色卡内嵌 character_book）
        val scanner = WorldInfoScanner(tokenCounter = tokenCounter)
        val wiResult = scanner.scan(
            chat = chatMessages.map { it.mes },
            maxContext = maxContextTokens,
            entries = parsed?.worldEntries ?: emptyList(),
            settings = WorldInfoSettings(),
            global = GlobalScanData(characterName = charName),
        )

        // 示例对话
        val examples = if (fields.mesExamples.isNotEmpty()) {
            val blocks = PromptUtils.parseMesExamples(fields.mesExamples)
            PromptPipeline.setOpenAIMessageExamples(blocks, userName, charName)
        } else emptyList()

        val result = PromptPipeline.prepare(
            PromptPipeline.PrepareInput(
                name2 = charName,
                charDescription = fields.description,
                charPersonality = fields.personality,
                scenario = fields.scenario,
                worldInfoBefore = wiResult.worldInfoBefore,
                worldInfoAfter = wiResult.worldInfoAfter,
                messages = historyMessages,
                messageExamples = examples,
                env = env,
                maxContextTokens = maxContextTokens,
                maxTokens = maxTokens,
                tokenCounter = tokenCounter,
                type = type,
                continuePrefill = continuePrefill,
                impersonationPrompt = impersonationPrompt,
                cyclePrompt = cyclePrompt,
                imageInlining = imageInlining,
                videoInlining = videoInlining,
                audioInlining = audioInlining,
            ),
        )
        return Prepared(
            messages = result.messages,
            activatedWorldInfo = wiResult.activated,
            counts = result.counts,
            maxContextTokens = maxContextTokens,
        )
    }

    private data class ParsedCard(
        val source: CharacterCardSource,
        val worldEntries: List<WorldInfoEntry>,
    )

    private fun parseCard(raw: String): ParsedCard {
        val root = json.parseToJsonElement(raw).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val source = CharacterCardSource(
            name = str(data, "name"),
            description = str(data, "description"),
            personality = str(data, "personality"),
            scenario = str(data, "scenario"),
            mesExample = str(data, "mes_example"),
            firstMessage = str(data, "first_mes"),
            systemPrompt = str(data, "system_prompt"),
            postHistoryInstructions = str(data, "post_history_instructions"),
            characterVersion = str(data, "character_version"),
            creatorNotes = str(data, "creator_notes"),
            depthPrompt = depthPromptOf(data),
            alternateGreetings = data["alternate_greetings"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
        )
        // 官方位置 data.character_book；兼容历史卡把 character_book 放根部的写法
        val book = data["character_book"]?.jsonObject ?: root["character_book"]?.jsonObject
        val entries = book?.get("entries")?.jsonArray
            ?.mapIndexedNotNull { i, el ->
                runCatching { WorldBookEntryParser.parse(el.jsonObject, "character", i) }.getOrNull()
            } ?: emptyList()
        return ParsedCard(source, entries)
    }

    /** 官方位置：data.extensions.depth_prompt.prompt；兼容旧版 data.depth_prompt 字符串/对象。 */
    private fun depthPromptOf(data: JsonObject): String {
        val fromExt = data["extensions"]?.jsonObject?.get("depth_prompt")?.jsonObject
            ?.get("prompt")?.jsonPrimitive?.contentOrNull
        if (!fromExt.isNullOrBlank()) return fromExt
        val legacy = data["depth_prompt"]
        val legacyObj = legacy as? JsonObject
        return legacyObj?.get("prompt")?.jsonPrimitive?.contentOrNull
            ?: (legacy as? JsonPrimitive)?.contentOrNull ?: ""
    }

    private fun mimeFromPath(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg", "oga" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }

    private fun str(obj: JsonObject, key: String): String =
        obj[key]?.jsonPrimitive?.contentOrNull ?: ""
}
