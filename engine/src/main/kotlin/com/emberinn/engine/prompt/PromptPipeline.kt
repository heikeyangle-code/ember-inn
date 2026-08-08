package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.worldinfo.TokenCounter

/**
 * 提示词总装器（对齐官方 openai.js prepareOpenAIMessages + populateChatCompletion）。
 *
 * 纪律：本类只做“顺序与传参”，业务逻辑全部留在各差分模块
 * （preparePromptsForChatCompletion / populateChatHistory / populateDialogueExamples / ChatCompletion / PromptUtils）。
 * 边界：absolute 提示的 in-chat 深度注入（populationInjectionPrompts）暂为原样透传，官方差分 fixture 不覆盖该分支。
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
        val disabledPromptIds: Set<String> = emptySet(),
        val newChatPrompt: String = "New chat:",
        val newExampleChatPrompt: String = "New chat:",
        val selectedGroup: Boolean = false,
        val namesBehavior: Int = PromptAssembler.NAMES_DEFAULT,
        val sendIfEmpty: String = "",
    )

    fun populate(
        chatCompletion: ChatCompletion,
        handler: TokenHandler,
        input: PopulateInput,
    ) {
        val prompts = input.prompts

        fun messageFromPrompt(p: PromptItem): CompletionMessage = CompletionMessage(
            role = p.role,
            content = p.content,
            name = p.name.ifEmpty { null },
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

        // 相对扩展提示注入 main（absolute 分支：官方会转成 injection 放 main 附近，本实现跳过）
        fun injectToMain(p: PromptItem) {
            if (chatCompletion.has("main")) {
                chatCompletion.insert(messageFromPrompt(p), "main", p.position ?: "end")
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
                // 官方：仅 names_behavior=COMPLETION 时 setName(sanitizeName(name))
                name = if (input.namesBehavior == PromptAssembler.NAMES_COMPLETION) chatMessage.name else null,
                identifier = "continuePrefill",
                tokens = handler.countAsync(content, "conversation"),
            )
            controlPrompts.add(continueMessage)
            chatCompletion.reserveBudget(continueMessage)
        }

        // in-chat 深度注入（populationInjectionPrompts）：absolute 提示暂原样透传（边界见类注释）
        val finalMessages = messages

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
                newChatPrompt = input.newChatPrompt,
                env = input.env,
                selectedGroup = input.selectedGroup,
                sendIfEmpty = input.sendIfEmpty,
                continuePrefill = input.continuePrefill,
                canUseTools = input.canUseTools,
                assistantPrefill = input.assistantPrefill,
                namesBehavior = input.namesBehavior,
            )
        } else {
            ChatHistoryPopulator.populate(
                messages = finalMessages,
                chatCompletion = chatCompletion,
                prompts = prompts,
                handler = handler,
                type = input.type,
                newChatPrompt = input.newChatPrompt,
                env = input.env,
                selectedGroup = input.selectedGroup,
                sendIfEmpty = input.sendIfEmpty,
                continuePrefill = input.continuePrefill,
                canUseTools = input.canUseTools,
                assistantPrefill = input.assistantPrefill,
                namesBehavior = input.namesBehavior,
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
        val disabledPromptIds: Set<String> = emptySet(),
        val newChatPrompt: String = "New chat:",
        val newExampleChatPrompt: String = "New chat:",
        val selectedGroup: Boolean = false,
        val namesBehavior: Int = PromptAssembler.NAMES_DEFAULT,
        val sendIfEmpty: String = "",
        val squashSystemMessages: Boolean = true,
    )

    data class PrepareResult(
        val messages: List<CompletionMessage>,
        val counts: Map<String, Int>,
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
        chatCompletion.setTokenBudget(input.maxContextTokens, input.maxTokens)

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
                disabledPromptIds = input.disabledPromptIds,
                newChatPrompt = input.newChatPrompt,
                newExampleChatPrompt = input.newExampleChatPrompt,
                selectedGroup = input.selectedGroup,
                namesBehavior = input.namesBehavior,
                sendIfEmpty = input.sendIfEmpty,
            ),
        )

        if (input.squashSystemMessages) chatCompletion.squashSystemMessages()
        return PrepareResult(
            messages = chatCompletion.getChat(),
            counts = handler.counts,
        )
    }
}
