package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.worldinfo.TokenCounter

/**
 * 提示词总装器（对齐官方 openai.js prepareOpenAIMessages + populateChatCompletion）。
 *
 * 纪律：本类只做“顺序与传参”，业务逻辑全部留在各差分模块
 * （preparePromptsForChatCompletion / populateChatHistory / populateDialogueExamples / ChatCompletion / PromptUtils）。
 * 边界：官方 getExtensionPrompt(IN_CHAT) 的内置扩展源暂为空（差分脚本同样打桩）；预算由 ChatCompletion 严格执行。
 */
object PromptPipeline {

    // ---------- 示例对话解析（官方 parseExampleIntoIndividual + setOpenAIMessageExamples） ----------

    fun parseExampleIntoIndividual(
        messageExampleString: String,
        name1: String,
        name2: String,
        appendNamesForGroup: Boolean = true,
        selectedGroup: Boolean = false,
        groupNames: List<String> = emptyList(),
    ): List<ExampleMessage> {
        val result = mutableListOf<ExampleMessage>()
        val tmp = messageExampleString.split('\n')
        var curMsgLines = mutableListOf<String>()
        var inUser = false
        var inBot = false
        var botName = name2
        val groupBotNames = groupNames.map { "$it:" }

        fun addMsg(name: String, role: String, systemName: String) {
            var parsed = curMsgLines.joinToString("\n").replace("$name:", "").trim()
            if (appendNamesForGroup && selectedGroup && (systemName == "example_user" || systemName == "example_assistant")) {
                parsed = "$name: $parsed"
            }
            result += ExampleMessage(name = systemName, content = parsed)
            curMsgLines = mutableListOf()
        }

        // 官方跳过第一行（"This is how {bot} should talk"）
        for (i in 1 until tmp.size) {
            val cur = tmp[i]
            if (cur.startsWith("$name1:")) {
                inUser = true
                if (inBot) addMsg(botName, "system", "example_assistant")
                inBot = false
            } else if (cur.startsWith("$name2:") || groupBotNames.any { cur.startsWith(it) }) {
                if (!cur.startsWith("$name2:") && groupBotNames.isNotEmpty()) {
                    botName = cur.split(":")[0]
                }
                inBot = true
                if (inUser) addMsg(name1, "system", "example_user")
                inUser = false
            }
            curMsgLines += cur
        }
        if (inUser) addMsg(name1, "system", "example_user")
        else if (inBot) addMsg(botName, "system", "example_assistant")
        return result
    }

    fun setOpenAIMessageExamples(
        mesExamplesArray: List<String>,
        name1: String,
        name2: String,
        selectedGroup: Boolean = false,
        groupNames: List<String> = emptyList(),
    ): List<List<ExampleMessage>> {
        val examples = mutableListOf<List<ExampleMessage>>()
        for (item in mesExamplesArray) {
            val replaced = item.replace(Regex("<START>", RegexOption.IGNORE_CASE), "{Example Dialogue:}").replace("\r", "")
            examples += parseExampleIntoIndividual(replaced, name1, name2, true, selectedGroup, groupNames)
        }
        return examples
    }

    // ---------- populationInjectionPrompts（官方 openai.js 1:1：absolute 提示按深度插入并反转） ----------

    /**
     * 官方 populationInjectionPrompts：absolute 提示按 injection_depth 分组，order 降序、角色
     * system/user/assistant 固定序，拼接后 splice 到 messages[depth+已插数]，最后整体 reverse()
     * （populateChatHistory 内部再 reverse 一次，净效果是顺序不变）。
     * inChatExtensions 对应官方 getExtensionPrompt(IN_CHAT, depth)：逐条 trim → separator 拼接 →
     * wrap 首尾补 separator → substituteParams（官方 openai.js getExtensionPrompt 语义）；
     * App 侧群聊深度提示（GroupDepthPromptsEngine）从这里注入（第 90 轮接线）。
     */
    fun populationInjectionPrompts(
        absolutePrompts: List<PromptItem>,
        messages: List<PromptMessage>,
        inChatExtensions: List<PromptItem> = emptyList(),
        env: MacroEnv = MacroEnv(user = "", char = ""),
    ): List<PromptMessage> {
        var totalInsertedMessages = 0
        val out = messages.toMutableList()
        val roleTypes = mapOf("system" to 0, "user" to 1, "assistant" to 2)
        val maxDepth = PromptInjection.DEFAULT_DEPTH

        for (depth in 0..maxDepth) {
            val depthPrompts = absolutePrompts.filter { it.injectionDepth == depth && it.content.isNotEmpty() }
            val roleMessages = mutableListOf<PromptMessage>()
            val separator = "\n"
            // 官方：预置 order=100 空组，保证只有扩展提示（in-chat）的深度也会走合并
            val orderGroups = linkedMapOf<Int, MutableList<PromptItem>>().apply { put(100, mutableListOf()) }
            for (prompt in depthPrompts) {
                val order = prompt.injectionOrder ?: 100
                orderGroups.getOrPut(order) { mutableListOf() }.add(prompt)
            }
            for (order in orderGroups.keys.sortedDescending()) {
                val orderPrompts = orderGroups[order] ?: continue
                for (role in listOf("system", "user", "assistant")) {
                    val rolePrompts = orderPrompts
                        .filter { it.role == role }
                        .joinToString(separator) { it.content }
                    // 官方：扩展提示只在 order==100 组合并（其余 order 组 extensionPrompt=''）；
                    // 官方 getExtensionPrompt(IN_CHAT, depth, '\n', role, wrap=true)：
                    // trim 拼接 → 首尾补 '\n' → substituteParams，最后与 rolePrompts 各自 trim 后 join
                    val extensionPrompt = if (order == 100) {
                        val raw = inChatExtensions
                            // 官方 getExtensionPrompt：Object.keys(extension_prompts).sort() 后按序拼接
                            .sortedBy { it.identifier }
                            .filter { it.injectionDepth == depth && it.role == role }
                            .map { it.content.trim() }
                            .joinToString(separator)
                        var wrapped = raw
                        if (wrapped.isNotEmpty() && !wrapped.startsWith(separator)) wrapped = separator + wrapped
                        if (wrapped.isNotEmpty() && !wrapped.endsWith(separator)) wrapped = wrapped + separator
                        if (wrapped.isNotEmpty()) wrapped = MacroEngine.substitute(wrapped, env)
                        wrapped
                    } else {
                        ""
                    }
                    val jointPrompt = listOf(rolePrompts, extensionPrompt)
                        .filter { it.isNotEmpty() }
                        .map { it.trim() }
                        .joinToString(separator)
                    if (jointPrompt.isNotEmpty()) {
                        roleMessages += PromptMessage(role = role, content = jointPrompt, injected = true)
                    }
                }
            }
            if (roleMessages.isNotEmpty()) {
                val injectIdx = depth + totalInsertedMessages
                // 官方 messages.splice(injectIdx, 0, ...)：插入点超过数组长度时追加到末尾，
                // Kotlin addAll(index) 会抛越界——深度 4 的角色卡深度提示在小历史下必须走 splice 语义
                out.addAll(injectIdx.coerceAtMost(out.size), roleMessages)
                totalInsertedMessages += roleMessages.size
            }
        }
        return out.reversed()
    }

    // ---------- populateChatCompletion（官方 openai.js 1:1 顺序） ----------

    data class PopulateInput(
        val prompts: PromptItems,
        val messages: List<PromptMessage>,
        val messageExamples: List<List<ExampleMessage>>,
        val bias: String = "",
        val quietPrompt: String = "",
        val type: String = "generate",
        val cyclePrompt: String = "",
        val env: MacroEnv,
        val pinExamples: Boolean = false,
        val continuePrefill: Boolean = false,
        val assistantPrefill: String = "",
        val chatCompletionSource: String = "openai",
        val canUseTools: Boolean = false,
        val toolBudgetReserve: Int = 0,
        val includeSignature: Boolean = false,
        val toolReasoningMode: String = ChatHistoryPopulator.TOOL_REASONING_DISABLED,
        val imageInlining: Boolean = false,
        val videoInlining: Boolean = false,
        val audioInlining: Boolean = false,
        val mediaTokenCosts: Map<String, Int> = emptyMap(),
        val disabledPromptIds: Set<String> = emptySet(),
        val newChatPrompt: String = "[Start a new Chat]",
        val newGroupChatPrompt: String = "[Start a new group chat. Group members: {{group}}]",
        val newExampleChatPrompt: String = "[Example Chat]",
        val continueNudgePrompt: String = "[Continue your last message without repeating its original content.]",
        val selectedGroup: Boolean = false,
        val namesBehavior: Int = PromptAssembler.NAMES_DEFAULT,
        val sendIfEmpty: String = "",
        val inChatExtensions: List<PromptItem> = emptyList(),
    )

    fun populate(
        chatCompletion: ChatCompletion,
        handler: TokenHandler,
        input: PopulateInput,
        continueNudgePrompt: String = "[Continue your last message without repeating its original content.]",
    ) {
        val prompts = input.prompts

        // 官方 Message.fromPromptAsync(prompt) = createAsync(role, content, identifier)：name 不复制
        fun messageFromPrompt(p: PromptItem): CompletionMessage = CompletionMessage(
            role = p.role,
            content = p.content,
            name = null,
            identifier = p.identifier,
            tokens = handler.countAsync(p.content, "prompt"),
        )

        fun addToChatCompletion(source: String, target: String? = null) {
            if (!prompts.has(source)) return
            if (input.disabledPromptIds.contains(source) && source != "main") return
            val prompt = prompts.get(source) ?: return
            if (prompt.injectionPosition == PromptInjection.ABSOLUTE) return
            val index = if (target != null) prompts.index(target) else prompts.index(source)
            val collection = CompletionCollection(source).apply { add(messageFromPrompt(prompt)) }
            chatCompletion.add(collection, index.takeIf { it >= 0 })
        }

        // every reply is primed with <|start|>assistant<|message|>
        chatCompletion.reserveBudget(3)

        // Character and world information
        addToChatCompletion("worldInfoBefore")
        addToChatCompletion("main")
        addToChatCompletion("worldInfoAfter")
        addToChatCompletion("charDescription")
        addToChatCompletion("charPersonality")
        addToChatCompletion("scenario")
        addToChatCompletion("personaDescription")

        // Control prompts（恒最后）
        chatCompletion.setOverriddenPrompts(prompts.overriddenPrompts)
        val controlPrompts = CompletionCollection("controlPrompts")
        if (input.type == "impersonate") {
            prompts.get("impersonate")?.let { controlPrompts.add(messageFromPrompt(it)) }
        }
        prompts.get("quietPrompt")?.let { if (it.content.isNotEmpty()) controlPrompts.add(messageFromPrompt(it)) }
        chatCompletion.reserveBudget(controlPrompts.getTokens())

        // Ordered system and user prompts
        val systemPrompts = listOf("nsfw", "jailbreak")
        val userRelative = prompts.collection
            .filter { !it.systemPrompt && it.injectionPosition != PromptInjection.ABSOLUTE }
            .map { it.identifier }
        for (identifier in systemPrompts + userRelative) addToChatCompletion(identifier)

        if (prompts.has("enhanceDefinitions")) addToChatCompletion("enhanceDefinitions")
        if (input.bias.isNotBlank() && prompts.has("bias")) addToChatCompletion("bias")

        // 相对扩展提示注入 main；main 缺失时官方把相对提示转成绝对注入（插到 absolutePrompts 中 main 附近）
        val absolutePrompts = prompts.collection
            .filter { it.injectionPosition == PromptInjection.ABSOLUTE }
            .toMutableList()
        fun injectToMain(p: PromptItem) {
            if (chatCompletion.has("main")) {
                chatCompletion.insert(messageFromPrompt(p), "main", p.position ?: "end")
            } else {
                val indexOfMain = absolutePrompts.indexOfFirst { it.identifier == "main" }
                if (indexOfMain >= 0) {
                    val main = absolutePrompts[indexOfMain]
                    val promptCopy = p.copy(
                        role = main.role,
                        injectionPosition = main.injectionPosition,
                        injectionDepth = main.injectionDepth,
                        injectionOrder = main.injectionOrder,
                    )
                    val newIndex = if (p.position == "end") indexOfMain + 1 else indexOfMain
                    absolutePrompts.add(newIndex.coerceAtMost(absolutePrompts.size), promptCopy)
                }
            }
        }
        val knownPrompts = listOf("summary", "authorsNote", "vectorsMemory", "vectorsDataBank", "smartContext")
        for (key in knownPrompts) {
            prompts.get(key)?.takeIf { it.position != null }?.let(::injectToMain)
        }
        prompts.collection.filter { it.extension && it.position != null }.forEach(::injectToMain)

        // 工具 token 预分配
        if (input.canUseTools && input.toolBudgetReserve > 0) {
            chatCompletion.reserveBudget(input.toolBudgetReserve)
        }

        // continue：移出最后一条，assistant + claude 时前置 assistant_prefill
        val messages = input.messages.toMutableList()
        if (input.type == "continue" && input.continuePrefill && messages.isNotEmpty()) {
            val chatMessage = messages.removeAt(0)
            val isAssistantRole = chatMessage.role == "assistant"
            val supportsAssistantPrefill = input.chatCompletionSource == "claude"
            val prefill = if (isAssistantRole && supportsAssistantPrefill) input.assistantPrefill else ""
            val content = listOf(prefill, chatMessage.content).filter { it.isNotEmpty() }.joinToString("\n\n")
            val continueMessage = CompletionMessage(
                role = chatMessage.role,
                content = content,
                // 官方：仅 COMPLETION 且原名存在时 setName(sanitizeName(name))
                name = if (input.namesBehavior == PromptAssembler.NAMES_COMPLETION && chatMessage.name != null) {
                    PromptNameSanitizer.sanitizeName(chatMessage.name)
                } else {
                    null
                },
                identifier = "continuePrefill",
                tokens = handler.countAsync(content, "conversation"),
            )
            controlPrompts.add(continueMessage)
            chatCompletion.reserveBudget(continueMessage)
        }

        // in-chat 深度注入（官方 populationInjectionPrompts；群聊深度提示等扩展从这里进）
        val finalMessages = populationInjectionPrompts(absolutePrompts, messages, input.inChatExtensions, input.env)

        // 示例/历史顺序
        if (input.pinExamples) {
            DialogueExamplesPopulator.populate(
                chatCompletion = chatCompletion,
                handler = handler,
                prompts = prompts,
                dialogues = input.messageExamples,
                newExampleChatPrompt = input.newExampleChatPrompt,
                env = input.env,
            )
            ChatHistoryPopulator.populate(
                messages = finalMessages,
                chatCompletion = chatCompletion,
                prompts = prompts,
                handler = handler,
                type = input.type,
                cyclePrompt = input.cyclePrompt,
                continueNudgePrompt = input.continueNudgePrompt,
                newChatPrompt = input.newChatPrompt,
                newGroupChatPrompt = input.newGroupChatPrompt,
                env = input.env,
                selectedGroup = input.selectedGroup,
                sendIfEmpty = input.sendIfEmpty,
                continuePrefill = input.continuePrefill,
                canUseTools = input.canUseTools,
                assistantPrefill = input.assistantPrefill,
                namesBehavior = input.namesBehavior,
                includeSignature = input.includeSignature,
                toolReasoningMode = input.toolReasoningMode,
                imageInlining = input.imageInlining,
                videoInlining = input.videoInlining,
                audioInlining = input.audioInlining,
                mediaTokenCosts = input.mediaTokenCosts,
            )
        } else {
            ChatHistoryPopulator.populate(
                messages = finalMessages,
                chatCompletion = chatCompletion,
                prompts = prompts,
                handler = handler,
                type = input.type,
                cyclePrompt = input.cyclePrompt,
                continueNudgePrompt = input.continueNudgePrompt,
                newChatPrompt = input.newChatPrompt,
                newGroupChatPrompt = input.newGroupChatPrompt,
                env = input.env,
                selectedGroup = input.selectedGroup,
                sendIfEmpty = input.sendIfEmpty,
                continuePrefill = input.continuePrefill,
                canUseTools = input.canUseTools,
                assistantPrefill = input.assistantPrefill,
                namesBehavior = input.namesBehavior,
                includeSignature = input.includeSignature,
                toolReasoningMode = input.toolReasoningMode,
                imageInlining = input.imageInlining,
                videoInlining = input.videoInlining,
                audioInlining = input.audioInlining,
                mediaTokenCosts = input.mediaTokenCosts,
            )
            DialogueExamplesPopulator.populate(
                chatCompletion = chatCompletion,
                handler = handler,
                prompts = prompts,
                dialogues = input.messageExamples,
                newExampleChatPrompt = input.newExampleChatPrompt,
                env = input.env,
            )
        }

        chatCompletion.freeBudget(controlPrompts.getTokens())
        if (controlPrompts.items.isNotEmpty()) chatCompletion.add(controlPrompts)
    }

    // ---------- prepareOpenAIMessages（官方 openai.js 顶层薄壳） ----------

    data class PrepareInput(
        val name2: String,
        val charDescription: String = "",
        val charPersonality: String = "",
        val scenario: String = "",
        val worldInfoBefore: String = "",
        val worldInfoAfter: String = "",
        val extensionPrompts: Map<String, ExtensionPrompt> = emptyMap(),
        val bias: String = "",
        val type: String = "generate",
        val quietPrompt: String = "",
        val cyclePrompt: String = "",
        val systemPromptOverride: String = "",
        val jailbreakPromptOverride: String = "",
        val messages: List<PromptMessage> = emptyList(),
        val messageExamples: List<List<ExampleMessage>> = emptyList(),
        val env: MacroEnv,
        val maxContextTokens: Int,
        val maxTokens: Int,
        val tokenCounter: TokenCounter,
        val userOrder: List<PromptOrderEntry> = emptyList(),
        val userPrompts: List<PromptItem> = emptyList(),
        val personaDescription: String = "",
        val personaInPrompt: Boolean = false,
        val impersonationPrompt: String = "",
        val personalityFormat: String = "{{personality}}",
        val scenarioFormat: String = "{{scenario}}",
        val groupNudge: String = "[Write the next reply only as {{char}}.]",
        val wiFormat: String = "{0}",
        val pinExamples: Boolean = false,
        val continuePrefill: Boolean = false,
        val assistantPrefill: String = "",
        val chatCompletionSource: String = "openai",
        val canUseTools: Boolean = false,
        val toolBudgetReserve: Int = 0,
        val includeSignature: Boolean = false,
        val toolReasoningMode: String = ChatHistoryPopulator.TOOL_REASONING_DISABLED,
        val imageInlining: Boolean = false,
        val videoInlining: Boolean = false,
        val audioInlining: Boolean = false,
        val mediaTokenCosts: Map<String, Int> = emptyMap(),
        val disabledPromptIds: Set<String> = emptySet(),
        val newChatPrompt: String = "[Start a new Chat]",
        val newGroupChatPrompt: String = "[Start a new group chat. Group members: {{group}}]",
        val newExampleChatPrompt: String = "[Example Chat]",
        val continueNudgePrompt: String = "[Continue your last message without repeating its original content.]",
        val selectedGroup: Boolean = false,
        val namesBehavior: Int = PromptAssembler.NAMES_DEFAULT,
        val sendIfEmpty: String = "",
        val squashSystemMessages: Boolean = false,
        val inChatExtensions: List<PromptItem> = emptyList(),
    )

    data class PrepareResult(
        val messages: List<CompletionMessage>,
        val counts: Map<String, Int>,
        /** 官方 PromptCollection.overriddenPrompts：被角色卡覆盖的提示项（main/jailbreak）。 */
        val overriddenPrompts: List<String> = emptyList(),
    )

    fun prepare(input: PrepareInput): PrepareResult {
        val prompts = PromptAssembler.preparePromptsForChatCompletion(
            scenario = input.scenario,
            charPersonality = input.charPersonality,
            name2 = input.name2,
            worldInfoBefore = input.worldInfoBefore,
            worldInfoAfter = input.worldInfoAfter,
            charDescription = input.charDescription,
            quietPrompt = input.quietPrompt,
            bias = input.bias,
            extensionPrompts = input.extensionPrompts,
            systemPromptOverride = input.systemPromptOverride,
            jailbreakPromptOverride = input.jailbreakPromptOverride,
            type = input.type,
            userOrder = input.userOrder,
            userPrompts = input.userPrompts,
            env = input.env,
            personaDescription = input.personaDescription,
            personaInPrompt = input.personaInPrompt,
            impersonationPrompt = input.impersonationPrompt,
            personalityFormat = input.personalityFormat,
            scenarioFormat = input.scenarioFormat,
            groupNudge = input.groupNudge,
            wiFormat = input.wiFormat,
        )
        return prepareWithPrompts(input, prompts)
    }

    /** 差分/测试入口：使用外部提供的提示集合（官方端到端 fixture 用同一集合）。 */
    fun prepareWithPrompts(input: PrepareInput, prompts: PromptItems): PrepareResult {
        val handler = TokenHandler(input.tokenCounter)
        val chatCompletion = ChatCompletion(handler)
        // 官方 openai.js prepareOpenAIMessages:1558：预算 = maxContext - maxTokens（不含 token_padding；
        // padding 只进 script.js 影子计数用于最终溢出检查，不参与 ChatCompletion 裁剪）
        chatCompletion.setTokenBudget(input.maxContextTokens, input.maxTokens)

        // 官方 prepareOpenAIMessages：TokenBudgetExceededError / InvalidCharacterNameError / 未知错误
        // 都只记录并继续（finally → getChat 返回能装下的部分消息）
        runCatching {
            populate(
            chatCompletion = chatCompletion,
            handler = handler,
            input = PopulateInput(
                prompts = prompts,
                messages = input.messages,
                messageExamples = input.messageExamples,
                bias = input.bias,
                quietPrompt = input.quietPrompt,
                type = input.type,
                cyclePrompt = input.cyclePrompt,
                env = input.env,
                pinExamples = input.pinExamples,
                continuePrefill = input.continuePrefill,
                assistantPrefill = input.assistantPrefill,
                chatCompletionSource = input.chatCompletionSource,
                canUseTools = input.canUseTools,
                toolBudgetReserve = input.toolBudgetReserve,
                includeSignature = input.includeSignature,
                toolReasoningMode = input.toolReasoningMode,
                imageInlining = input.imageInlining,
                videoInlining = input.videoInlining,
                audioInlining = input.audioInlining,
                mediaTokenCosts = input.mediaTokenCosts,
                disabledPromptIds = input.disabledPromptIds,
                newChatPrompt = input.newChatPrompt,
                newGroupChatPrompt = input.newGroupChatPrompt,
                newExampleChatPrompt = input.newExampleChatPrompt,
                continueNudgePrompt = input.continueNudgePrompt,
                selectedGroup = input.selectedGroup,
                namesBehavior = input.namesBehavior,
                sendIfEmpty = input.sendIfEmpty,
                inChatExtensions = input.inChatExtensions,
            ),
            )
        }

        if (input.squashSystemMessages) chatCompletion.squashSystemMessages()
        return PrepareResult(
            messages = chatCompletion.getChat(),
            counts = handler.counts,
            overriddenPrompts = prompts.overriddenPrompts.toList(),
        )
    }
}
