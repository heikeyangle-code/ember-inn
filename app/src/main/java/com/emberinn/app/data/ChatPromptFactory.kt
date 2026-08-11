package com.emberinn.app.data

import com.emberinn.engine.macros.ChatMessage
import com.emberinn.engine.macros.CharacterFields
import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.macros.EmptyVariableStore
import com.emberinn.engine.macros.MemoryVariableStore
import com.emberinn.engine.macros.VariableStore
import com.emberinn.engine.macros.SystemFields
import com.emberinn.engine.media.MediaAttachment
import com.emberinn.engine.media.MediaEngine
import com.emberinn.engine.prompt.CharacterCardFieldsEngine
import com.emberinn.engine.prompt.ExtensionPrompt
import com.emberinn.engine.prompt.PromptItem
import com.emberinn.engine.prompt.AuthorsNoteBuilder
import com.emberinn.engine.prompt.AuthorsNoteEngine
import com.emberinn.engine.prompt.AuthorsNoteMetadata
import com.emberinn.engine.prompt.AuthorsNoteSettings
import com.emberinn.engine.prompt.BiasEngine
import com.emberinn.engine.prompt.CharacterCardSource
import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.prompt.PromptAssembler
import com.emberinn.engine.prompt.PromptPipeline
import com.emberinn.engine.prompt.PromptReasoningEngine
import com.emberinn.engine.prompt.ReasoningPromptSettings
import com.emberinn.engine.prompt.PromptUtils
import com.emberinn.engine.regex.RegexPipelineEngine
import com.emberinn.engine.regex.RegexScopeResolver
import com.emberinn.engine.regex.RegexPipelineScript
import com.emberinn.engine.worldinfo.GlobalScanData
import com.emberinn.engine.worldinfo.TokenCounterFactory
import com.emberinn.engine.worldinfo.VectorChatMessage
import com.emberinn.engine.worldinfo.mapExtensionPosition
import com.emberinn.engine.worldinfo.VectorChatRearranger
import com.emberinn.engine.worldinfo.VectorChatSettings
import com.emberinn.engine.worldinfo.VectorFileRef
import com.emberinn.engine.worldinfo.VectorSettings
import com.emberinn.engine.worldinfo.VectorStore
import com.emberinn.engine.worldinfo.WorldBookEntryParser
import com.emberinn.engine.worldinfo.WorldInfoEntry
import com.emberinn.engine.worldinfo.WorldInfoScanner
import com.emberinn.engine.worldinfo.WorldInfoSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
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
        /** 官方 regex_placement（engine.js）：USER_INPUT=1 / AI_OUTPUT=2 / WORLD_INFO=5。 */
        const val REGEX_USER_INPUT = 1
        const val REGEX_AI_OUTPUT = 2
        const val REGEX_SLASH_COMMAND = 3
        const val REGEX_WORLD_INFO = 5
        const val REGEX_REASONING = 6

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

    /** 官方 /inject 的 script_injects 条目（chat_metadata.script_injects）。 */
    data class ScriptInject(
        val id: String,
        val value: String,
        val position: String, // before / after / chat / none（官方 injectCallback 位置）
        val depth: Int = 4,
        val role: String = "system", // system / user / assistant
        val scan: Boolean = false,
    )

    /**
     * 官方 getRegexScripts({ allowedOnly: true }) 的 App 侧统一解析：
     * GLOBAL → PRESET → SCOPED；scoped 仅当角色头像在 character_allowed_regex 中。
     * 发送/落盘（sendMessageAsUser/saveReply/getFirstMessage）与总装共用同一脚本集合。
     */
    fun resolveRegexScripts(
        characterRawJson: String?,
        globalRegexScripts: List<RegexPipelineScript>,
        scopedAllowed: Boolean = false,
        presetScripts: List<RegexPipelineScript> = emptyList(),
        presetAllowed: Boolean = false,
    ): List<RegexPipelineScript> {
        val parsed = characterRawJson?.let { runCatching { parseCard(it) }.getOrNull() }
        return RegexScopeResolver.resolve(
            global = globalRegexScripts,
            preset = presetScripts,
            scoped = parsed?.regexScripts ?: emptyList(),
            allowedOnly = true,
            presetAllowed = presetAllowed,
            scopedAllowed = scopedAllowed,
        )
    }

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
        personaDescription: String = "",
        personaInPrompt: Boolean = false,
        vectorStore: VectorStore? = null,
        vectorChatSettings: VectorChatSettings = VectorChatSettings(),
        vectorWorldSettings: VectorSettings = VectorSettings(),
        vectorDataBank: List<VectorFileRef> = emptyList(),
        vectorFileText: (String) -> String? = { null },
        extensionPrompts: Map<String, ExtensionPrompt> = emptyMap(),
        inChatExtensions: List<PromptItem> = emptyList(),
        worldInfoSettings: WorldInfoSettings = WorldInfoSettings(),
        globalRegexScripts: List<RegexPipelineScript> = emptyList(),
        regexScopedAllowed: Boolean = false,
        regexPresetScripts: List<RegexPipelineScript> = emptyList(),
        regexPresetAllowed: Boolean = false,
        isContinue: Boolean = false,
        regexEnabled: Boolean = true,
        reasoningToPrompts: Boolean = false,
        scriptInjections: List<ScriptInject> = emptyList(),
        /** 会话级变量存储（官方聊天级 local variables）：ChatRepository 每会话一份，setvar 跨消息保留。 */
        localVariables: VariableStore = EmptyVariableStore,
    ): Prepared {
        val parsed = characterRawJson?.let { runCatching { parseCard(it) }.getOrNull() }
        // 官方 script.js：chat_metadata.system_prompt/scenario/mes_example 覆盖角色卡字段
        val fields = CharacterCardFieldsEngine.fields(
            character = parsed?.source,
            chatMetadataSystem = chatMetadata?.get("system_prompt")?.jsonPrimitive?.contentOrNull ?: "",
            chatMetadataScenario = chatMetadata?.get("scenario")?.jsonPrimitive?.contentOrNull ?: "",
            chatMetadataMesExample = chatMetadata?.get("mes_example")?.jsonPrimitive?.contentOrNull ?: "",
        )
        // 对齐官方 MacroEnvBuilder：character 字段来自 getCharacterCardFields（已 baseChatReplace）。
        // 变量宏接线：优先用调用方传入的会话级存储（ChatRepository 每会话一份，setvar 跨消息保留）；
        // 未传时回退为“每轮预置本卡变量（extensions.emberinn_variables）”的内存存储（getvar 可读）。
        val local = if (localVariables === EmptyVariableStore) {
            MemoryVariableStore().apply {
                CharacterCardEdit.readVariables(characterRawJson.orEmpty()).forEach { (k, v) -> set(k, v) }
            }
        } else {
            localVariables
        }
        var env = MacroEnv(
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
            local = local,
        )
        val tokenCounter = TokenCounterFactory.forModel(model)
        // 官方 getRegexedString：getRegexScripts({ allowedOnly: true })，
        // GLOBAL → PRESET → SCOPED；scoped 仅当角色头像在 character_allowed_regex 中、
        // preset 仅当当前预设名在 preset_allowed_regex[api] 中（App 暂无预设脚本存储，preset 恒空）。
        val regexScripts = resolveRegexScripts(
            characterRawJson = characterRawJson,
            globalRegexScripts = globalRegexScripts,
            scopedAllowed = regexScopedAllowed,
            presetScripts = regexPresetScripts,
            presetAllowed = regexPresetAllowed,
        )

        // 历史消息（JSONL → 引擎 ChatMessage → PromptMessage）；Pair.first 保留原始 JSONL 下标，
        // 保证 extra.media 挂回正确的消息（曾有 mes 缺失导致下标错位、附件挂错消息的隐患）
        val indexedChatMessages = history.mapIndexedNotNull { index, el ->
            val obj = el.jsonObject
            // 官方 script.js coreChat：is_system（含 /hide 隐藏、comment 注释消息）不进提示词；
            // 旧版 is_hidden 字段一并兼容排除
            if (obj["is_system"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true ||
                obj["is_hidden"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true
            ) {
                return@mapIndexedNotNull null
            }
            val isUser = obj["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true
            val mes = obj["mes"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            index to ChatMessage(
                mes = mes,
                isUser = isUser,
                name = obj["name"]?.jsonPrimitive?.contentOrNull,
            )
        }
        // 官方 script.js generate coreChat.map：消息在存前已过一轮（sendMessageAsUser/saveReply，
        // 本 App 未落盘改写、登记边界），总装时按官方再应用一次（isPrompt=true + depth）。
        // depth = coreChat.length - index - (isContinue ? 2 : 1)，coreChat 不含系统消息。
        val chatMessages = indexedChatMessages.mapIndexed { i, (index, m) ->
            if (regexScripts.isEmpty() || !regexEnabled) index to m
            else index to m.copy(
                mes = RegexPipelineEngine.apply(
                    raw = m.mes,
                    placement = if (m.isUser) REGEX_USER_INPUT else REGEX_AI_OUTPUT,
                    scripts = regexScripts,
                    isPrompt = true,
                    depth = indexedChatMessages.size - i - (if (isContinue) 2 else 1),
                    characterOverride = charName,
                    disabledExtensions = if (regexEnabled) emptySet() else setOf("regex"),
                ),
            )
        }.let { msgs ->
            // 官方 getBiasStrings：{{bias:...}} 提取自输入文本（regenerate/swipe 时回溯 extra.bias）；
            // impersonate/continue 不注入 bias。宏从消息文本剥离（官方 Handlebars helper 渲染为空）。
            // 宏从所有用户消息剥离（避免 {{bias:...}} 泄漏进提示词，含 continue 里的旧消息）；
            // bias 只取最后一条用户消息，且仅 generate/swipe 注入（官方 getBiasStrings 对 impersonate/continue 返回空）
            var found = ""
            var lastUserBias = ""
            val cleaned = msgs.map { pair ->
                val (index, m) = pair
                if (m.isUser && m.mes.contains("{{bias")) {
                    val (text, bias) = extractMessageBias(m.mes)
                    // 官方 getBiasStrings 取“最后一条用户消息”的 bias；逐条覆盖，最后一条用户消息胜出
                    if (bias.isNotBlank()) lastUserBias = bias
                    index to m.copy(mes = text)
                } else pair
            }
            if (type != "impersonate" && type != "continue" && lastUserBias.isNotBlank()) found = lastUserBias
            cleaned to found
        }
        val (indexedMessages, textBias) = chatMessages
        // 官方 getBiasStrings：优先回溯最后一条用户消息 extra.bias（编辑消息时官方把 {{bias}} 存进 extra.bias）
        val storedBias = history.lastOrNull { el ->
            val obj = el.jsonObject
            val isUser = obj["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true
            isUser && (obj["extra"] as? JsonObject)?.get("bias")?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
        }?.jsonObject?.get("extra")?.jsonObject?.get("bias")?.jsonPrimitive?.contentOrNull
        val promptBias = storedBias ?: textBias
        // 官方 coreChat.map：extra.append_title + 媒体 append_title 标题追加到消息正文末尾
        val titledMessages = indexedMessages.map { (idx, m) ->
            val extra = history.getOrNull(idx)?.jsonObject?.get("extra") as? JsonObject
            val titles = mutableListOf<String>()
            if (extra?.get("append_title")?.jsonPrimitive?.content == "true") {
                extra["title"]?.jsonPrimitive?.contentOrNull?.let { titles += it }
            }
            extra?.get("media")?.jsonArray?.forEach { me ->
                val mo = me.jsonObject
                if (mo["append_title"]?.jsonPrimitive?.content == "true") {
                    mo["title"]?.jsonPrimitive?.contentOrNull?.let { titles += it }
                }
            }
            idx to m.copy(titles = titles)
        }
        val cleanMessages = titledMessages.map { it.second }

        // 向量 RAG（官方 extensions/vectors）：聊天历史重排 + 文件/数据银行 + 世界书向量激活。
        // 引擎 VectorChatRearranger 1:1；App 只负责把数据接进去，结果进历史/扩展提示/强制激活。
        val vectorTransform = if (vectorStore != null &&
            (vectorChatSettings.enabledChats || vectorChatSettings.enabledFiles || vectorWorldSettings.enabled)
        ) {
            val vectorChat = cleanMessages.mapIndexed { _, m ->
                VectorChatMessage(name = m.name.orEmpty(), mes = m.mes)
            }
            VectorChatRearranger.rearrange(
                chat = vectorChat,
                store = vectorStore,
                // 官方 substituteParamsExtended：查询/模板文本过宏替换（{{user}}/{{char}} 等）
                settings = vectorChatSettings.copy(macroSubstituter = { MacroEngine.substitute(it, env) }),
                worldInfoEntries = parsed?.worldEntries ?: emptyList(),
                worldInfoSettings = vectorWorldSettings,
                dataBankFiles = vectorDataBank,
                fileTextResolver = vectorFileText,
            )
        } else {
            null
        }

        // 对齐官方 setOpenAIMessages：进总装前消息是“新的在前”（official messages[i] 反向填充）；
        // 总装内部 populationInjectionPrompts 会 reverse 一次、历史填充再 reverse 一次。
        // 之前传“旧的在前”导致 continue_prefill 把最老消息当续写对象。
        // 向量重排后消息顺序/内容以引擎结果为准；原 JSONL 下标用于携带 extra.media。
        var indexedChat = vectorTransform?.let { transform ->
            mapVectorMessages(transform.newChat, titledMessages)
        } ?: titledMessages
        // 官方 openai.js prepareMessages：历史 AI 消息的 extra.reasoning 先过 REASONING 正则
        // （isPrompt=true + depth），再按 PromptReasoning.addToMessage 注入正文。
        // add_to_prompts 默认关（非 prefix 不注入）；continue 最后一条 prefix 不受开关限制（官方语义）。
        val reasoningEngine = PromptReasoningEngine(substitute = { MacroEngine.substitute(it, env) })
        indexedChat = indexedChat.mapIndexed { i, pair ->
            val (idx, m) = pair
            val el = history.getOrNull(idx)?.jsonObject
            val extra = el?.get("extra") as? JsonObject
            val reasoning = extra?.get("reasoning")?.jsonPrimitive?.contentOrNull
            if (reasoning.isNullOrEmpty()) {
                pair
            } else {
                val duration = extra["reasoning_duration"]?.jsonPrimitive?.content?.toLongOrNull()
                val depth = indexedChat.size - i - (if (isContinue) 2 else 1)
                val regexed = RegexPipelineEngine.apply(
                    raw = reasoning,
                    placement = REGEX_REASONING,
                    scripts = regexScripts,
                    isPrompt = true,
                    depth = depth,
                    characterOverride = charName,
                    disabledExtensions = if (regexEnabled) emptySet() else setOf("regex"),
                )
                val isPrefix = isContinue && i == indexedChat.lastIndex
                idx to m.copy(
                    mes = reasoningEngine.addToMessage(
                        content = m.mes,
                        reasoning = regexed,
                        isPrefix = isPrefix,
                        duration = duration,
                        settings = ReasoningPromptSettings(addToPrompts = reasoningToPrompts),
                    ),
                )
            }
        }
        val openAiMessages = PromptAssembler.toOpenAiMessages(indexedChat.map { it.second }, user = userName)
        val historyMessages = openAiMessages.mapIndexed { i, pm ->
            val el = history.getOrNull(indexedChat[i].first)?.jsonObject
            val extra = el?.get("extra") as? JsonObject
            val media = extra?.get("media")?.jsonArray?.mapNotNull { me ->
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
            }.orEmpty()
            pm.copy(
                identifier = "chatHistory",
                media = media,
                // 对齐官方 script.js getMediaDisplay：extra 优先、无效回退 LIST（不再手写白名单）
                mediaDisplay = MediaEngine.getMediaDisplay(
                    extraMediaDisplay = extra?.get("media_display")?.jsonPrimitive?.contentOrNull,
                    powerUserMediaDisplay = null,
                ),
                // 对齐官方 getMediaIndex：数字/字符串原样、越界/负数/NaN 回退 0，null 透传
                mediaIndex = MediaEngine.getMediaIndex(
                    mediaCount = media.size,
                    mediaIndex = extra?.get("media_index"),
                ).let { el ->
                    if (el is JsonNull) null else el.jsonPrimitive.content.toIntOrNull() ?: 0
                },
            )
        }
            .asReversed()

        // 世界书扫描（角色卡内嵌 character_book）
        val scanner = WorldInfoScanner(
            tokenCounter = tokenCounter,
            // 官方 world-info.js BUILDING PROMPT：getRegexedString(entry.content, WORLD_INFO,
            // { depth: regexDepth, isMarkdown: false, isPrompt: true })
            contentTransformer = { content, regexDepth, _ ->
                RegexPipelineEngine.apply(
                    raw = content,
                    placement = REGEX_WORLD_INFO,
                    scripts = regexScripts,
                    isPrompt = true,
                    depth = regexDepth,
                    characterOverride = charName,
                    disabledExtensions = if (regexEnabled) emptySet() else setOf("regex"),
                )
            },
        )
        // 官方 /inject：script_injects → setExtensionPrompt(script_inject_{id}, value, position, depth, scan, role)
        // before→start / after→end / chat→in_chat / none→不注入（仅存元数据，可配合 scan 触发世界书）
        val scriptExtensionPrompts = mutableMapOf<String, ExtensionPrompt>()
        val scriptInChatPrompts = mutableListOf<PromptItem>()
        val scriptScanInjections = mutableListOf<String>()
        for (inj in scriptInjections) {
            if (inj.value.isBlank()) continue
            if (inj.scan) scriptScanInjections += inj.value
            val identifier = "script_inject_${inj.id}"
            when (inj.position) {
                "before" -> scriptExtensionPrompts[identifier] = ExtensionPrompt(identifier, inj.role, inj.value, "start", inj.depth)
                "after" -> scriptExtensionPrompts[identifier] = ExtensionPrompt(identifier, inj.role, inj.value, "end", inj.depth)
                "chat" -> scriptInChatPrompts += PromptItem(
                    identifier = identifier,
                    name = "脚本注入 ${inj.id}",
                    content = inj.value,
                    role = inj.role,
                    injectionDepth = inj.depth,
                    injectionOrder = 100,
                )
                // none：官方不注入提示词
            }
        }

        val wiResult = scanner.scan(
            chat = indexedChat.map { it.second.mes } + scriptScanInjections,
            maxContext = maxContextTokens,
            entries = parsed?.worldEntries ?: emptyList(),
            settings = worldInfoSettings,
            global = GlobalScanData(characterName = charName),
            // 官方 WorldInfoBuffer.externalActivations：向量检索命中的条目强制激活（跳过关键词/概率）
            externalActivations = vectorTransform?.worldInfoActivations.orEmpty()
                .associateBy { "${it.world}.${it.uid}" },
        )

        // 官方 script.js：outletEntries → setExtensionPrompt(CUSTOM_WI_OUTLET(key), value, NONE, 0)，
        // 仅供 {{outlet::key}} 宏读取（NONE 不注入提示词）
        env = env.copy(outlets = wiResult.outletEntries.mapValues { (_, v) -> v.joinToString("\n") })

        // 官方 script.js：worldInfoDepth → setExtensionPrompt(CUSTOM_WI_DEPTH_ROLE, IN_CHAT, depth, role)
        val worldInfoDepthPrompts = wiResult.depthEntries.mapIndexed { i, d ->
            PromptItem(
                identifier = "worldInfoDepth$i",
                name = "世界书深度 ${d.depth}/${d.role}",
                content = d.entries.joinToString("\n"),
                role = d.role,
                injectionDepth = d.depth,
                injectionOrder = 100,
            )
        }

        // 示例对话：官方先建卡内 mes_example，再把世界书 EM 锚点示例 unshift/push 进同一数组
        val exampleBlocks = mutableListOf<String>()
        exampleBlocks += PromptUtils.parseMesExamples(fields.mesExamples)
        for (em in wiResult.emEntries) {
            val parsed = PromptUtils.parseMesExamples(em.content)
            if (em.position == 0) {
                parsed.reversed().forEach { exampleBlocks.add(0, it) }
            } else {
                exampleBlocks += parsed
            }
        }
        val examples = PromptPipeline.setOpenAIMessageExamples(exampleBlocks, userName, charName)

        // 官方 authors-note.js：note_prompt/note_position/note_depth/note_role + ANWithWI 合并世界书 AN 前后注入
        val note = AuthorsNoteEngine.resolve(
            meta = AuthorsNoteMetadata(
                prompt = chatMetadata?.get("note_prompt")?.jsonPrimitive?.contentOrNull,
                interval = chatMetadata?.get("note_interval")?.jsonPrimitive?.content?.toIntOrNull(),
                position = chatMetadata?.get("note_position")?.jsonPrimitive?.content?.toIntOrNull(),
                depth = chatMetadata?.get("note_depth")?.jsonPrimitive?.content?.toIntOrNull(),
                role = chatMetadata?.get("note_role")?.jsonPrimitive?.content?.toIntOrNull(),
            ),
            settings = AuthorsNoteSettings(),
        )
        // 官方 authors-note.js：lastMessageNumber >= interval ? lastMessageNumber % interval : interval - lastMessageNumber，
        // shouldAdd = messagesTillInsertion == 0（即消息数恰为 interval 的倍数才注入）
        val noteInterval = note.interval.coerceAtLeast(1)
        val historyCount = history.size
        val shouldInjectNote = historyCount >= noteInterval && historyCount % noteInterval == 0
        val noteContent = if (shouldInjectNote) note.content else ""
        val anText = AuthorsNoteBuilder.compose(noteContent, wiResult.anBefore, wiResult.anAfter, note.allowWIScan)
        // 官方 setExtensionPrompt：position=IN_CHAT(1) 走 getExtensionPrompt(IN_CHAT)（populationInjectionPrompts），
        // 其余（0=IN_PROMPT→end、2=BEFORE_PROMPT→start）走扩展提示注入

        var effectiveInChat = inChatExtensions + scriptInChatPrompts
        var effectiveExtensions = extensionPrompts + scriptExtensionPrompts
        if (anText.isNotBlank()) {
            if (note.position == 1) {
                effectiveInChat = effectiveInChat + PromptItem(
                    identifier = "authorsNote",
                    name = "作者注释",
                    content = anText,
                    role = note.role,
                    injectionDepth = note.depth,
                    injectionOrder = 100,
                )
            } else {
                mapExtensionPosition(note.position)?.let { pos ->
                    effectiveExtensions = effectiveExtensions + (
                        "2_floating_prompt" to ExtensionPrompt("2_floating_prompt", note.role, anText, pos, note.depth)
                    )
                }
            }
        }

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
                personaDescription = personaDescription,
                personaInPrompt = personaInPrompt,
                extensionPrompts = effectiveExtensions + vectorTransform?.extensionPrompts.orEmpty(),
                inChatExtensions = effectiveInChat + worldInfoDepthPrompts,
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

    /**
     * 向量重排后消息 → 原 JSONL 下标 + 更新后的 ChatMessage。
     * 引擎只返回 VectorChatMessage；按 name+mes 匹配原消息（文件分块注入时 mes 前缀变长，用后缀匹配），
     * 未匹配时按剩余顺序兜底。media 等 extra 字段仍从原 JSONL 取。
     */
    private fun mapVectorMessages(
        reordered: List<VectorChatMessage>,
        source: List<Pair<Int, ChatMessage>>,
    ): List<Pair<Int, ChatMessage>> {
        val used = mutableSetOf<Int>()
        val byNameAndMes = source.indices.groupBy { source[it].second.name to source[it].second.mes }
        return reordered.map { vm ->
            val idx = byNameAndMes[vm.name to vm.mes]?.firstOrNull { it !in used }
                ?: source.indices.firstOrNull { i -> i !in used && source[i].second.name == vm.name && vm.mes.endsWith(source[i].second.mes) }
                ?: source.indices.firstOrNull { it !in used }
                ?: 0
            used += idx
            source[idx].first to source[idx].second.copy(mes = vm.mes)
        }
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

    /** 官方 extractMessageBias + removeMacros（引擎 1:1，Handlebars 语义）。 */
    private fun extractMessageBias(message: String): Pair<String, String> {
        val bias = BiasEngine.extractMessageBias(message)
        val cleaned = if (bias.isNotBlank()) BiasEngine.removeMacros(message) else message
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
