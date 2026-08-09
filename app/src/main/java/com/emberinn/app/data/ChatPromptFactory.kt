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
import com.emberinn.engine.regex.RegexPipelineEngine
import com.emberinn.engine.regex.RegexPipelineScript
import com.emberinn.engine.worldinfo.GlobalScanData
import com.emberinn.engine.worldinfo.TokenCounterFactory
import com.emberinn.engine.worldinfo.WorldBookEntryParser
import com.emberinn.engine.worldinfo.WorldInfoEntry
import com.emberinn.engine.worldinfo.WorldInfoScanner
import com.emberinn.engine.worldinfo.WorldInfoSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 提示词工厂：角色卡 + 聊天历史 → PromptPipeline 总装 → 最终 CompletionMessage。
 * App 发消息唯一入口（世界书/人设/作者注释/示例/历史全部在引擎内完成）。
 */
class ChatPromptFactory {

    /** 对齐官方 openai.js default_impersonation_prompt。 */
    companion object {
        /** 官方 regex_placement（engine.js）：USER_INPUT=1 / AI_OUTPUT=2。 */
        const val REGEX_USER_INPUT = 1
        const val REGEX_AI_OUTPUT = 2

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
        chatMetadata: JsonObject? = null,
        chatCompletionSource: String = "openai",
    ): Prepared {
        val parsed = characterRawJson?.let { runCatching { parseCard(it) }.getOrNull() }
        // 官方 script.js：chat_metadata.system_prompt/scenario/mes_example 覆盖角色卡字段
        val fields = CharacterCardFieldsEngine.fields(
            character = parsed?.source,
            chatMetadataSystem = chatMetadata?.get("system_prompt")?.jsonPrimitive?.contentOrNull ?: "",
            chatMetadataScenario = chatMetadata?.get("scenario")?.jsonPrimitive?.contentOrNull ?: "",
            chatMetadataMesExample = chatMetadata?.get("mes_example")?.jsonPrimitive?.contentOrNull ?: "",
        )
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
        val regexScripts = parsed?.regexScripts ?: emptyList()

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
        }.map { m ->
            // 官方：用户输入存前过 USER_INPUT 正则、生成回复存前过 AI_OUTPUT 正则（script.js sendMessageAsUser/saveReply）；
            // 本 App 在总装时统一应用（对幂等脚本等价；双应用边界见 HANDOFF）
            if (regexScripts.isEmpty()) m
            else m.copy(
                mes = RegexPipelineEngine.apply(
                    raw = m.mes,
                    placement = if (m.isUser) REGEX_USER_INPUT else REGEX_AI_OUTPUT,
                    scripts = regexScripts,
                    characterOverride = charName,
                ),
            )
        }.let { msgs ->
            // 官方 getBiasStrings：{{bias:...}} 提取自输入文本（regenerate/swipe 时回溯 extra.bias）；
            // impersonate/continue 不注入 bias。宏从消息文本剥离（官方 Handlebars helper 渲染为空）。
            // 宏从所有用户消息剥离（避免 {{bias:...}} 泄漏进提示词，含 continue 里的旧消息）；
            // bias 只取最后一条用户消息，且仅 generate/swipe 注入（官方 getBiasStrings 对 impersonate/continue 返回空）
            var found = ""
            var lastUserBias = ""
            val cleaned = msgs.mapIndexed { i, m ->
                if (m.isUser && m.mes.contains("{{bias")) {
                    val (text, bias) = extractMessageBias(m.mes)
                    if (i == msgs.lastIndex) lastUserBias = bias
                    m.copy(mes = text)
                } else m
            }
            if (type != "impersonate" && type != "continue" && lastUserBias.isNotBlank()) found = lastUserBias
            cleaned to found
        }
        val (cleanMessages, promptBias) = chatMessages

        // 对齐官方 setOpenAIMessages：进总装前消息是“新的在前”（official messages[i] 反向填充）；
        // 总装内部 populationInjectionPrompts 会 reverse 一次、历史填充再 reverse 一次。
        // 之前传“旧的在前”导致 continue_prefill 把最老消息当续写对象。
        val historyMessages = PromptAssembler.toOpenAiMessages(cleanMessages, user = userName)
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
            chat = cleanMessages.map { it.mes },
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
                // 官方 script.js generate：systemPromptOverride = 角色 system_prompt（元数据优先），jailbreak 同理
                systemPromptOverride = fields.system,
                jailbreakPromptOverride = fields.jailbreak,
                bias = promptBias,
                chatCompletionSource = chatCompletionSource,
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
        val regexScripts: List<RegexPipelineScript>,
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
        // 该卡正则（官方 char-data.js RegexScriptData → 引擎 RegexPipelineScript）
        val regexScripts = data["extensions"]?.jsonObject?.get("regex_scripts")?.jsonArray
            ?.mapNotNull { el ->
                val e = el.jsonObject
                runCatching {
                    RegexPipelineScript(
                        findRegex = (e["findRegex"] as? JsonPrimitive)?.contentOrNull ?: "",
                        replaceString = (e["replaceString"] as? JsonPrimitive)?.contentOrNull ?: "",
                        trimStrings = (e["trimStrings"] as? JsonArray)
                            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList(),
                        disabled = (e["disabled"] as? JsonPrimitive)?.booleanOrNull ?: false,
                        substituteRegex = (e["substituteRegex"] as? JsonPrimitive)?.intOrNull ?: 0,
                        placement = (e["placement"] as? JsonArray)
                            ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: listOf(1, 2, 5, 6),
                        markdownOnly = (e["markdownOnly"] as? JsonPrimitive)?.booleanOrNull ?: false,
                        promptOnly = (e["promptOnly"] as? JsonPrimitive)?.booleanOrNull ?: false,
                        runOnEdit = (e["runOnEdit"] as? JsonPrimitive)?.booleanOrNull ?: true,
                        minDepth = (e["minDepth"] as? JsonPrimitive)?.intOrNull,
                        maxDepth = (e["maxDepth"] as? JsonPrimitive)?.intOrNull,
                    )
                }.getOrNull()
            } ?: emptyList()
        return ParsedCard(source, entries, regexScripts)
    }

    /**
     * 对齐官方 extractMessageBias（script.js）：提取 {{bias:...}} 内容并从消息中移除宏。
     * 官方用 Handlebars helper；本实现用非贪婪正则（嵌套/引号边界近似，登记见 HANDOFF）。
     */
    private fun extractMessageBias(message: String): Pair<String, String> {
        val pattern = Regex("""\{\{\s*bias\s*:([\s\S]*?)\s*\}\}""")
        val matches = pattern.findAll(message).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        val cleaned = pattern.replace(message, "")
        val bias = if (matches.isEmpty()) "" else " " + matches.joinToString(" ")
        return cleaned to bias
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
