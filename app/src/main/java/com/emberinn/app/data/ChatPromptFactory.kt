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
import com.emberinn.engine.prompt.CfgPromptEngine
import com.emberinn.engine.prompt.CharacterCardFieldsEngine
import com.emberinn.engine.prompt.ExtensionPrompt
import com.emberinn.engine.prompt.PromptItem
import com.emberinn.engine.prompt.PromptOrderEntry
import com.emberinn.engine.prompt.AuthorsNoteBuilder
import com.emberinn.engine.prompt.AuthorsNoteEngine
import com.emberinn.engine.prompt.AuthorsNoteMetadata
import com.emberinn.engine.prompt.AuthorsNoteSettings
import com.emberinn.engine.prompt.CharaNote
import com.emberinn.engine.prompt.BiasEngine
import com.emberinn.engine.prompt.BiasChatMessage
import com.emberinn.engine.prompt.BiasConfig
import com.emberinn.engine.prompt.CharacterCardSource
import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.prompt.DepthPromptEngine
import com.emberinn.engine.prompt.ExampleAssembler
import com.emberinn.engine.prompt.ExtensionPromptEngine
import com.emberinn.engine.prompt.MemoryEngine
import com.emberinn.engine.prompt.PromptAssembler
import com.emberinn.engine.prompt.PromptPipeline
import com.emberinn.engine.prompt.PromptReasoningEngine
import com.emberinn.engine.prompt.ReasoningPromptSettings
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
import com.emberinn.engine.worldinfo.WorldLoreMerger
import com.emberinn.engine.worldinfo.WorldInfoSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
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

        /** 角色卡解析缓存上限（同一张卡只解析一次；卡 JSON 大时是发送前的主要开销）。 */
        private const val CARD_CACHE_MAX = 8
        /** 角色卡解析缓存：访问序 LRU（accessOrder=true），超限淘汰最久未用的一张；纯 App 层提速。 */
        private val cardCache: MutableMap<String, ParsedCard> =
            java.util.Collections.synchronizedMap(LinkedHashMap<String, ParsedCard>(16, 0.75f, true))
    }

    private val json = Json { ignoreUnknownKeys = true }

    data class Prepared(
        val messages: List<CompletionMessage>,
        val activatedWorldInfo: List<WorldInfoEntry>,
        val counts: Map<String, Int> = emptyMap(),
        val maxContextTokens: Int = 4095,
        /** CFG Scale 命中档（官方 cfgGuidanceScale；null=未启用）与负向提示，供 textgen/novel 请求体消费。 */
        val cfgGuidanceScale: JsonElement? = null,
        val cfgNegativePrompt: String = "",
    )

    /** 官方 /inject 的 script_injects 条目（chat_metadata.script_injects），引擎 1:1 模型。 */
    typealias ScriptInject = ExtensionPromptEngine.ScriptInject

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

    /** 显示位点宏环境（对齐官方 messageFormatting 的 substituteParams 环境：角色卡字段 + 用户/角色名）。
     *  官方显示宏使用全量环境；聊天级变量等仍由 prepare 的会话存储注入。 */
    fun displayMacroEnv(
        userName: String,
        charName: String,
        characterRawJson: String?,
        localVariables: VariableStore = EmptyVariableStore,
    ): MacroEnv {
        val parsed = characterRawJson?.let { runCatching { parseCard(it) }.getOrNull() }
        val fields = CharacterCardFieldsEngine.fields(character = parsed?.source)
        // 与 prepare 相同的官方 MacroEnvBuilder 映射：getCharacterCardFields 结果 → MacroEnv.character
        return MacroEnv(
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
            local = localVariables,
        )
    }

    fun prepare(
        characterRawJson: String?,
        history: List<JsonElement>,
        userName: String,
        charName: String,
        model: String,
        maxContextTokens: Int = 4095,
        maxTokens: Int = 300,
        type: String = "generate",
        /** 官方 Generate 的 textareaText（getBiasStrings 用；regenerate/swipe/continue 空输入回溯 extra.bias）。 */
        textareaText: String = "",
        continuePrefill: Boolean = false,
        impersonationPrompt: String = DEFAULT_IMPERSONATION_PROMPT,
        cyclePrompt: String = "",
        imageInlining: Boolean = false,
        videoInlining: Boolean = false,
        audioInlining: Boolean = false,
        chatMetadata: JsonObject? = null,
        chatCompletionSource: String = "openai",
        /** 官方 CFG Scale（cfg-scale.js）：全局/角色/会话配置；仅 textgen/novel 请求体消费 guidance。 */
        cfgGlobal: CfgPromptEngine.CfgGlobal = CfgPromptEngine.CfgGlobal(),
        cfgChara: CfgPromptEngine.CfgChara? = null,
        cfgChat: CfgPromptEngine.CfgChat = CfgPromptEngine.CfgChat(),
        cfgSelectedGroup: Boolean = false,
        personaDescription: String = "",
        personaInPrompt: Boolean = false,
        /** 官方 persona_description_positions：0=IN_PROMPT/2=TOP_AN/3=BOTTOM_AN/4=AT_DEPTH/9=NONE。 */
        personaPosition: Int = 0,
        personaDepth: Int = 2,
        personaRole: Int = 0,
        anSettings: AuthorsNoteSettings = AuthorsNoteSettings(),
        charaNote: CharaNote? = null,
        vectorStore: VectorStore? = null,
        vectorChatSettings: VectorChatSettings = VectorChatSettings(),
        vectorWorldSettings: VectorSettings = VectorSettings(),
        vectorDataBank: List<VectorFileRef> = emptyList(),
        vectorFileText: (String) -> String? = { null },
        extensionPrompts: Map<String, ExtensionPrompt> = emptyMap(),
        inChatExtensions: List<PromptItem> = emptyList(),
        /** Prompt Manager 用户提示项（官方 serviceSettings.prompts；空=默认集）。 */
        userPrompts: List<PromptItem> = emptyList(),
        /** Prompt Manager 用户顺序（官方 prompt_order；空=调用方注入默认顺序）。 */
        userOrder: List<PromptOrderEntry> = emptyList(),
        worldInfoSettings: WorldInfoSettings = WorldInfoSettings(),
        globalRegexScripts: List<RegexPipelineScript> = emptyList(),
        regexScopedAllowed: Boolean = false,
        regexPresetScripts: List<RegexPipelineScript> = emptyList(),
        regexPresetAllowed: Boolean = false,
        isContinue: Boolean = false,
        regexEnabled: Boolean = true,
        reasoningToPrompts: Boolean = false,
        /** 官方 power_user.reasoning.prefix/suffix/separator（reasoning 预设应用后生效）。 */
        reasoningTemplate: com.emberinn.engine.prompt.ReasoningTemplate = com.emberinn.engine.prompt.ReasoningTemplate(),
        scriptInjections: List<ScriptInject> = emptyList(),
        /** 官方 generate：群聊有 depth 提示时用群聊深度提示，否则用角色卡深度提示（DEPTH_PROMPT）。 */
        useCharacterDepthPrompt: Boolean = true,
        /** 官方 oai_settings.squash_system_messages：总装后合并连续 system 消息；dryRun/textgen 不生效。 */
        squashSystemMessages: Boolean = false,
        /** 官方 oai_settings.names_behavior（消息名字处理模式）。 */
        namesBehavior: Int = 0,
        /** 官方 oai_settings.send_if_empty（末条 assistant 时补发文本）。 */
        sendIfEmpty: String = "",
        /** 官方 oai_settings.new_chat_prompt / new_group_chat_prompt / new_example_chat_prompt。 */
        newChatPrompt: String = "[Start a new Chat]",
        newGroupChatPrompt: String = "[Start a new group chat. Group members: {{group}}]",
        newExampleChatPrompt: String = "[Example Chat]",
        /** 官方 oai_settings.continue_nudge_prompt（continue nudge 模板）。 */
        continueNudgePrompt: String = "[Continue your last message without repeating its original content.]",
        /** 官方 oai_settings.wi_format / scenario_format / personality_format / group_nudge_prompt。 */
        wiFormat: String = "{0}",
        scenarioFormat: String = "{{scenario}}",
        personalityFormat: String = "{{personality}}",
        groupNudgePrompt: String = "[Write the next reply only as {{char}}.]",
        /** 官方 oai_settings.assistant_prefill（Claude continue 预填）。 */
        assistantPrefill: String = "",
        /** 官方 oai_settings.tool_reasoning_mode（工具推理链注入模式）。 */
        toolReasoningMode: String = com.emberinn.engine.prompt.ChatHistoryPopulator.TOOL_REASONING_DISABLED,
        /** 官方 ToolManager.isToolCallingSupported：本轮是否允许工具调用（App 按注册工具/能力填充）。 */
        canUseTools: Boolean = false,
        /** 官方 generateQuietPrompt 的 quietPrompt（记忆扩展 DEFAULT 总结器等场景）。 */
        quietPrompt: String = "",
        /** 官方 memory 扩展：最新摘要 + 注入设置（formatMemoryValue → setExtensionPrompt('1_memory')）。 */
        memorySummary: String = "",
        memoryTemplate: String = MemoryEngine.DEFAULT_TEMPLATE,
        memoryPosition: Int = 0,
        memoryRole: Int = 0,
        memoryDepth: Int = 2,
        memoryScan: Boolean = false,
        /** 官方 power_user.collapse_newlines（字段/示例折叠连续换行）。 */
        collapseNewlines: Boolean = false,
        /** 官方 power_user.context.example_separator（非 OpenAI 消息示例分隔符，默认 ***）。 */
        exampleSeparator: String = "***",
        /** 官方外置世界书：worlds 目录的 *.json 条目（App WorldStore 加载）。 */
        externalWorlds: Map<String, List<WorldInfoEntry>> = emptyMap(),
        /** 官方 data.extensions.world：角色关联的外置世界。 */
        linkedWorld: String? = null,
        /** 官方 chat_metadata.world_info：本会话指定世界。 */
        chatMetadataWorld: String? = null,
        /** 官方 settings.world_info.globalSelect：全局生效世界。 */
        globalWorlds: List<String> = emptyList(),
        /** 官方 world_info_insertion_strategy：CHARACTER_FIRST/GLOBAL_FIRST/EVENLY。 */
        worldInsertStrategy: Int = WorldLoreMerger.CHARACTER_FIRST,
        /** 官方 world_info_include_names：世界书扫描文本带 "name: mes" 前缀。 */
        wiIncludeNames: Boolean = true,
        /** 官方 setOpenAIMessages isSameModel：当前 API/模型（extra.api/extra.model 比对）。 */
        currentApi: String = "",
        currentModel: String = "",
        /** 官方 power_user.user_prompt_bias / show_user_prompt_bias。 */
        userPromptBias: String = "",
        /** 官方 power_user.pin_examples（示例固定顶部）。 */
        pinExamples: Boolean = false,
        /** 官方 power_user.strip_examples（完全移除示例对话）。 */
        stripExamples: Boolean = false,
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
            collapseNewlines = collapseNewlines,
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
        // 官方 memory 扩展 {{summary}} 宏 + formatMemoryValue 注入
        env = env.copy(summary = memorySummary)
        val memoryFormatted = if (memorySummary.isNotBlank()) {
            MemoryEngine.formatMemoryValue(memorySummary, memoryTemplate) {
                MacroEngine.substitute(it, env.copy(summary = memorySummary))
            }
        } else {
            ""
        }
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
            // 官方 script.js coreChat：is_system 不进提示词，例外是工具调用系统消息
            // （canUseTools && Array.isArray(extra.tool_invocations)）；旧版 is_hidden 一并兼容排除
            if (obj["is_system"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true ||
                obj["is_hidden"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true
            ) {
                val extra = obj["extra"] as? JsonObject
                val toolInvocations = extra?.get("tool_invocations")?.jsonArray
                if (!(canUseTools && toolInvocations != null && toolInvocations.isNotEmpty())) {
                    return@mapIndexedNotNull null
                }
            }
            val isUser = obj["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true
            val mes = obj["mes"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            val toolInvocations = (obj["extra"] as? JsonObject)?.get("tool_invocations")?.jsonArray
                ?.mapNotNull { el ->
                    val t = el.jsonObject
                    val name = t["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val id = t["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    com.emberinn.engine.prompt.ToolInvocation(
                        id = id,
                        name = name,
                        parameters = t["parameters"]?.jsonPrimitive?.contentOrNull ?: "{}",
                        result = t["result"]?.jsonPrimitive?.contentOrNull ?: "",
                        reasoning = t["reasoning"]?.jsonPrimitive?.contentOrNull,
                        signature = t["signature"]?.jsonPrimitive?.contentOrNull,
                    )
                }
            index to ChatMessage(
                mes = mes,
                isUser = isUser,
                isSystem = obj["is_system"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true,
                name = obj["name"]?.jsonPrimitive?.contentOrNull,
                toolInvocations = toolInvocations,
                api = (obj["extra"] as? JsonObject)?.get("api")?.jsonPrimitive?.contentOrNull,
                model = (obj["extra"] as? JsonObject)?.get("model")?.jsonPrimitive?.contentOrNull,
                reasoningSignature = (obj["extra"] as? JsonObject)?.get("reasoning_signature")?.jsonPrimitive?.contentOrNull,
                reasoning = (obj["extra"] as? JsonObject)?.get("reasoning")?.jsonPrimitive?.contentOrNull,
                narrator = (obj["extra"] as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "narrator",
                forceAvatar = obj["force_avatar"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true,
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
                    substitute = { MacroEngine.substitute(it, env) },
                    disabledExtensions = if (regexEnabled) emptySet() else setOf("regex"),
                ),
            )
        }.let { msgs ->
            // 官方 sendMessageAsUser 存前已剥离 {{bias}} 并写入 extra.bias；旧/导入聊天兜底再清一次
            // （官方 Handlebars bias helper 渲染为空，等价不泄漏），但不从历史文本反推 bias。
            msgs.map { pair ->
                val (index, m) = pair
                if (m.isUser && m.mes.contains("{{bias")) {
                    index to m.copy(mes = extractMessageBias(m.mes).first)
                } else pair
            }
        }
        val indexedMessages = chatMessages
        // 官方 Generate.getBiasStrings(textareaText, type)：
        // 输入为空时回溯最近一条 user/system/narrator 的 extra.bias（swipe 跳过最后一条），
        // 否则用输入文本里的 {{bias}}（发送时已存 extra.bias）；impersonate/continue 恒空。
        val promptBias = BiasEngine.getBiasStrings(
            textareaText = textareaText,
            type = type,
            config = BiasConfig(
                userPromptBias = userPromptBias,
                chat = history.mapNotNull { el ->
                    val obj = el.jsonObject
                    val isUser = obj["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true
                    val isSystem = obj["is_system"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true
                    val isNarrator = (obj["extra"] as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "narrator"
                    BiasChatMessage(
                        isUser = isUser,
                        isSystem = isSystem,
                        isNarrator = isNarrator,
                        bias = (obj["extra"] as? JsonObject)?.get("bias")?.jsonPrimitive?.contentOrNull,
                    )
                },
            ),
        ).promptBias
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
                    substitute = { MacroEngine.substitute(it, env) },
                    disabledExtensions = if (regexEnabled) emptySet() else setOf("regex"),
                )
                val isPrefix = isContinue && i == indexedChat.lastIndex
                idx to m.copy(
                    mes = reasoningEngine.addToMessage(
                        content = m.mes,
                        reasoning = regexed,
                        isPrefix = isPrefix,
                        duration = duration,
                        settings = ReasoningPromptSettings(
                            addToPrompts = reasoningToPrompts,
                            prefix = reasoningTemplate.prefix,
                            suffix = reasoningTemplate.suffix,
                            separator = reasoningTemplate.separator,
                        ),
                    ),
                )
            }
        }
        // 官方 setOpenAIMessages：输出“新的在前”；历史下标同步反转，保证 media/reasoning 挂回正确消息
        val pipelineChat = indexedChat.asReversed()
        val openAiMessages = PromptAssembler.toOpenAiMessages(
            chat = indexedChat.map { it.second },
            user = userName,
            name2 = charName,
            currentApi = currentApi,
            currentModel = currentModel,
        )
        val historyMessages = openAiMessages.mapIndexed { i, pm ->
            val el = history.getOrNull(pipelineChat[i].first)?.jsonObject
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

        // 世界书扫描（角色卡内嵌 character_book）
        val scanner = WorldInfoScanner(
            tokenCounter = tokenCounter,
            // 官方 checkWorldInfo：关键词与条目内容先 substituteParams 再匹配/预算/输出
            // （内容激活时替换一次，总装 preparePrompt 再替换一次 = 官方两次替换语义）
            substitute = { MacroEngine.substitute(it, env) },
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
                    substitute = { MacroEngine.substitute(it, env) },
                    disabledExtensions = if (regexEnabled) emptySet() else setOf("regex"),
                )
            },
        )
        // 官方 /inject：script_injects → processChatSlashCommands 逐条 setExtensionPrompt，
        // 引擎 ExtensionPromptEngine 1:1 落成 before→start / after→end / chat→in-chat / none→不注入；
        // scan=true 的 value 按官方 getExtensionPromptByName 先宏替换再进世界书扫描缓冲
        val scriptPlan = ExtensionPromptEngine.planScriptInjections(scriptInjections) {
            MacroEngine.substitute(it, env)
        }

        val wiResult = scanner.scan(
            // 官方：chatForWI = coreChat.map(x => include_names ? `${x.name}: ${x.mes}` : x.mes).reverse()
            chat = indexedChat.map { it.second }.map { m ->
                if (wiIncludeNames) "${m.name ?: ""}: ${m.mes}" else m.mes
            },
            maxContext = maxContextTokens,
            // 官方 getSortedEntries：内嵌卡书 + 角色关联外置世界 + 聊天指定 + 全局选择 合并
            entries = WorldLoreMerger.merge(
                global = globalWorlds.flatMap { externalWorlds[it].orEmpty() },
                character = (parsed?.worldEntries ?: emptyList()) +
                    (linkedWorld?.let { externalWorlds[it].orEmpty() } ?: emptyList()),
                chat = chatMetadataWorld?.let { externalWorlds[it].orEmpty() } ?: emptyList(),
                persona = emptyList(),
                strategy = worldInsertStrategy,
            ),
            settings = worldInfoSettings,
            global = GlobalScanData(
                characterName = charName,
                personaDescription = personaDescription,
                // 官方 getTagKeyForEntity 恒返回 tagMap 项（无标签=[]）；角色卡标签直接进过滤
                characterTags = parsed?.tags ?: emptyList(),
            ),
            // 官方 WorldInfoBuffer.externalActivations：向量检索命中的条目强制激活（跳过关键词/概率）
            externalActivations = vectorTransform?.worldInfoActivations.orEmpty()
                .associateBy { "${it.world}.${it.uid}" },
            // 官方 checkWorldInfo：scan=true 的扩展提示 addInject 进扫描缓冲（不在聊天深度里）
            scanInjections = scriptPlan.scanValues +
                if (memoryScan && memoryFormatted.isNotEmpty()) listOf(memoryFormatted) else emptyList(),
        )

        // 官方 script.js：outletEntries → setExtensionPrompt(CUSTOM_WI_OUTLET(key), value, NONE, 0)，
        // 仅供 {{outlet::key}} 宏读取（NONE 不注入提示词）
        env = env.copy(outlets = wiResult.outletEntries.mapValues { (_, v) -> v.joinToString("\n") })

        // 官方 script.js：flushWIInjections + worldInfoDepth.forEach →
        // setExtensionPrompt(CUSTOM_WI_DEPTH_ROLE(depth, role), IN_CHAT, depth, role)
        val worldInfoDepthPrompts = DepthPromptEngine.worldInfoDepthPromptItems(wiResult.depthEntries)

        // 示例对话：官方先建卡内 mes_example，再把世界书 EM 锚点示例 unshift/push 进同一数组
        // 官方：WI 示例先 baseChatReplace（宏替换+collapse+去 \r）再 parseMesExamples，
        // before(0)→unshift / after(1)→push（此前 App 缺 baseChatReplace，已补）
        val exampleBlocks = ExampleAssembler.assembleWithWorldExamples(
            baseMesExamples = fields.mesExamples,
            emEntries = wiResult.emEntries,
            substitute = { MacroEngine.substitute(it, env) },
            collapseNewlines = collapseNewlines,
            isInstruct = false,
            exampleSeparator = exampleSeparator,
            mainApiIsOpenAi = chatCompletionSource != "claude",
        )
        // 官方 script.js：power_user.strip_examples → mesExamplesArray = []（完全移除示例）
        val examples = if (stripExamples) {
            emptyList()
        } else {
            PromptPipeline.setOpenAIMessageExamples(exampleBlocks, userName, charName)
        }

        // 官方 authors-note.js：note_prompt/note_position/note_depth/note_role + ANWithWI 合并世界书 AN 前后注入
        val note = AuthorsNoteEngine.resolve(
            meta = AuthorsNoteMetadata(
                prompt = chatMetadata?.get("note_prompt")?.jsonPrimitive?.contentOrNull,
                interval = chatMetadata?.get("note_interval")?.jsonPrimitive?.content?.toIntOrNull(),
                position = chatMetadata?.get("note_position")?.jsonPrimitive?.content?.toIntOrNull(),
                depth = chatMetadata?.get("note_depth")?.jsonPrimitive?.content?.toIntOrNull(),
                role = chatMetadata?.get("note_role")?.jsonPrimitive?.content?.toIntOrNull(),
            ),
            settings = anSettings,
        )
        // 官方 authors-note.js：统计“用户消息数”而非总消息数，interval 1 恒注入
        val userMessageCount = history.count { el ->
            val obj = el.jsonObject
            obj["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true
        }
        val shouldInjectNote = AuthorsNoteEngine.shouldInjectNote(userMessageCount, note.interval)
        val noteContent = if (shouldInjectNote) {
            // 官方 authors-note.js：角色备注 useChara 时 before/after/replace 合并
            AuthorsNoteEngine.applyCharaNote(note.content, charaNote)
        } else {
            ""
        }
        val anTextRaw = AuthorsNoteBuilder.compose(noteContent, wiResult.anBefore, wiResult.anAfter, note.allowWIScan)
        // 官方 script.js：persona_description_position=TOP_AN(2)/BOTTOM_AN(3) 时把人设描述合并进作者注释
        val anText = when (personaPosition) {
            2 -> if (personaDescription.isNotBlank()) "$personaDescription\n$anTextRaw" else anTextRaw
            3 -> if (personaDescription.isNotBlank()) "$anTextRaw\n$personaDescription" else anTextRaw
            else -> anTextRaw
        }
        // 官方 setExtensionPrompt：position=IN_CHAT(1) 走 getExtensionPrompt(IN_CHAT)（populationInjectionPrompts），
        // 其余（0=IN_PROMPT→end、2=BEFORE_PROMPT→start）走扩展提示注入

        var effectiveInChat = inChatExtensions + scriptPlan.inChatPrompts
        var effectiveExtensions = (extensionPrompts + scriptPlan.extensionPrompts).toMutableMap()
        // 官方 generate：群聊有 depth 提示时用群聊深度提示（调用方 inChatExtensions），
        // 否则角色卡深度提示 setExtensionPrompt(DEPTH_PROMPT, IN_CHAT, depth, role)
        if (useCharacterDepthPrompt) {
            DepthPromptEngine.characterDepthPromptItem(
                content = fields.charDepthPrompt,
                depth = parsed?.depthPromptDepth ?: DepthPromptEngine.DEFAULT_DEPTH,
                role = parsed?.depthPromptRole ?: DepthPromptEngine.DEFAULT_ROLE,
            )?.let { effectiveInChat += it }
        }
        // 官方 script.js：persona_description_position=AT_DEPTH(4) → setExtensionPrompt(PERSONA_DESCRIPTION, IN_CHAT, depth, role)
        if (personaPosition == 4 && personaDescription.isNotBlank()) {
            effectiveInChat = effectiveInChat + PromptItem(
                identifier = "PERSONA_DESCRIPTION",
                name = "人设描述",
                content = personaDescription,
                role = ExtensionPromptEngine.roleName(personaRole),
                injectionDepth = personaDepth,
                injectionOrder = 100,
            )
        }
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
                    effectiveExtensions["2_floating_prompt"] = ExtensionPrompt(
                        "2_floating_prompt", note.role, anText, pos, note.depth,
                    )
                }
            }
        }

        // 官方 memory 扩展 setExtensionPrompt('1_memory', formatMemoryValue(...), position, depth, scan, role)
        if (memoryFormatted.isNotEmpty()) {
            val memoryRoleName = ExtensionPromptEngine.roleName(memoryRole)
            when (memoryPosition) {
                ExtensionPromptEngine.POSITION_IN_CHAT -> effectiveInChat += PromptItem(
                    identifier = "1_memory",
                    name = "记忆",
                    content = memoryFormatted,
                    role = memoryRoleName,
                    injectionDepth = memoryDepth,
                    injectionOrder = 100,
                )
                ExtensionPromptEngine.POSITION_BEFORE_PROMPT -> effectiveExtensions["1_memory"] =
                    ExtensionPrompt("1_memory", memoryRoleName, memoryFormatted, "start", memoryDepth)
                ExtensionPromptEngine.POSITION_IN_PROMPT -> effectiveExtensions["1_memory"] =
                    ExtensionPrompt("1_memory", memoryRoleName, memoryFormatted, "end", memoryDepth)
                // POSITION_NONE：官方不注入
            }
        }

        // 官方 CFG Scale：getGuidanceScale 优先级 + 正/负提示 + 上下文扣减 + 正向注入（getCombinedPrompt 语义）
        var cfgEffectiveMaxContext = maxContextTokens
        var cfgPositiveInjection: PromptItem? = null
        var cfgNegativePrompt = ""
        val cfgGuidance = CfgPromptEngine.getGuidanceScale(cfgGlobal, cfgChara, cfgChat, cfgSelectedGroup)
        if (cfgGuidance != null && cfgGuidance.value != 1.0) {
            val g = cfgGuidance
            val cfgSubstitute: (String) -> String = { MacroEngine.substitute(it, env) }
            val neg = CfgPromptEngine.getCfgPrompt(g, true, cfgChat, cfgChara, cfgGlobal, cfgSubstitute).value
            val pos = CfgPromptEngine.getCfgPrompt(g, false, cfgChat, cfgChara, cfgGlobal, cfgSubstitute).value
            cfgNegativePrompt = neg
            // 官方：useCfgPrompt 时 maxContext 扣减 max(neg,pos) token（对全部后端生效）
            cfgEffectiveMaxContext = (maxContextTokens - maxOf(tokenCounter.count(neg), tokenCounter.count(pos))).coerceAtLeast(0)
            // 官方 getCombinedPrompt(false)：openai 跳过；其余把正向提示注入 finalMesSend
            if (chatCompletionSource != "openai" && pos.isNotEmpty()) {
                val depth = cfgChat.promptInsertionDepth
                // 官方：depth==0 直接追加末条（空格规则）；否则 extensionPrompts 插到 max(len-depth,0) 位
                val promptDepth = if (depth == 0) 0 else (historyMessages.size - depth).coerceAtLeast(0)
                cfgPositiveInjection = PromptItem(
                    identifier = "cfg_prompt",
                    name = "CFG",
                    content = pos,
                    role = "system",
                    injectionDepth = promptDepth,
                    injectionOrder = 100,
                )
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
                inChatExtensions = effectiveInChat + worldInfoDepthPrompts + listOfNotNull(cfgPositiveInjection),
                userPrompts = userPrompts,
                userOrder = userOrder,
                maxContextTokens = cfgEffectiveMaxContext,
                maxTokens = maxTokens,
                tokenCounter = tokenCounter,
                type = type,
                // 官方 script.js generate：systemPromptOverride = 角色 system_prompt（元数据优先），jailbreak 同理
                systemPromptOverride = fields.system,
                jailbreakPromptOverride = fields.jailbreak,
                bias = promptBias,
                chatCompletionSource = chatCompletionSource,
                canUseTools = canUseTools,
                continuePrefill = continuePrefill,
                impersonationPrompt = impersonationPrompt,
                cyclePrompt = cyclePrompt,
                squashSystemMessages = squashSystemMessages,
                namesBehavior = namesBehavior,
                sendIfEmpty = sendIfEmpty,
                newChatPrompt = newChatPrompt,
                newGroupChatPrompt = newGroupChatPrompt,
                newExampleChatPrompt = newExampleChatPrompt,
                continueNudgePrompt = continueNudgePrompt,
                wiFormat = wiFormat,
                scenarioFormat = scenarioFormat,
                personalityFormat = personalityFormat,
                groupNudge = groupNudgePrompt,
                assistantPrefill = assistantPrefill,
                toolReasoningMode = toolReasoningMode,
                imageInlining = imageInlining,
                videoInlining = videoInlining,
                audioInlining = audioInlining,
                quietPrompt = quietPrompt,
                pinExamples = pinExamples,
            ),
        )
        return Prepared(
            messages = result.messages,
            activatedWorldInfo = wiResult.activated,
            counts = result.counts,
            maxContextTokens = cfgEffectiveMaxContext,
            cfgGuidanceScale = cfgGuidance?.let { g ->
                buildJsonObject {
                    put("type", JsonPrimitive(g.type))
                    put("value", g.value?.let(::JsonPrimitive) ?: JsonNull)
                }
            },
            cfgNegativePrompt = cfgNegativePrompt,
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
        /** 官方 data.extensions.depth_prompt.depth ?? 4。 */
        val depthPromptDepth: Int = 4,
        /** 官方 data.extensions.depth_prompt.role ?? 'system'。 */
        val depthPromptRole: String = "system",
        /** 官方 tag_map 的角色标签（无标签=空数组，官方 getTagKeyForEntity 恒有 tagMap 项）。 */
        val tags: List<String> = emptyList(),
    )

    /** 预热：打开聊天/切角色时后台解析当前卡，让第一次发送直接命中缓存（App 层提速）。 */
    fun warmCardCache(characterRawJson: String?) {
        if (characterRawJson.isNullOrBlank()) return
        runCatching { parseCard(characterRawJson) }
    }

    private fun parseCard(raw: String): ParsedCard {
        synchronized(cardCache) {
            cardCache[raw]?.let { return it }
        }
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
        // 官方 tag_map：角色卡 tags 数组（无标签=[]，官方 getTagKeyForEntity 恒有 tagMap 项）
        val tags = (data["tags"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.isNotBlank() } ?: emptyList()
        val result = ParsedCard(
            source = source,
            worldEntries = entries,
            regexScripts = regexScripts,
            tags = tags,
            depthPromptDepth = data["extensions"]?.jsonObject?.get("depth_prompt")?.jsonObject
                ?.get("depth")?.jsonPrimitive?.content?.toIntOrNull() ?: 4,
            depthPromptRole = data["extensions"]?.jsonObject?.get("depth_prompt")?.jsonObject
                ?.get("role")?.jsonPrimitive?.contentOrNull?.ifBlank { "system" } ?: "system",
        )
        synchronized(cardCache) {
            // 并发下可能已被其它线程解析：命中直接复用
            cardCache[raw]?.let { return it }
            cardCache[raw] = result
            // LRU 淘汰：超限时移除最久未访问的一张（accessOrder 首元素）
            while (cardCache.size > CARD_CACHE_MAX) {
                val eldest = cardCache.entries.iterator()
                if (!eldest.hasNext()) break
                eldest.next()
                eldest.remove()
            }
        }
        return result
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
