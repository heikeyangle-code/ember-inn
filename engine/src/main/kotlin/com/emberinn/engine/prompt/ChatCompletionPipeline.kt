package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv

/**
 * populateChatCompletion（对齐官方 openai.js 核心）：
 * 按 PromptManager 顺序把提示放进根集合 → control prompts（impersonate/quiet）
 * → nsfw/jailbreak/用户相对提示/enhanceDefinitions/bias → 相对扩展注入 main
 * → in-chat 深度注入（populationInjectionPrompts）→ 历史/示例（pin_examples 决定先后）
 * → control prompts 最后追加。边界：工具预留、continue prefill。
 */
object ChatCompletionPipeline {

    private val KNOWN_RELATIVE = listOf("summary", "authorsNote", "vectorsMemory", "vectorsDataBank", "smartContext")
    private val FIXED_ORDER = listOf(
        "worldInfoBefore", "main", "worldInfoAfter", "charDescription",
        "charPersonality", "scenario", "personaDescription",
    )

    fun populate(
        prompts: PromptItems,
        chatCompletion: ChatCompletion,
        handler: TokenHandler,
        env: MacroEnv,
        type: String,
        messages: List<PromptMessage>,
        messageExamples: List<List<ExampleMessage>>,
        newChatPrompt: String,
        newExampleChatPrompt: String,
        selectedGroup: Boolean = false,
        sendIfEmpty: String = "",
        pinExamples: Boolean = false,
        inChatExtensions: List<ExtensionPrompt> = emptyList(),
        cyclePrompt: String = "",
        continueNudgePrompt: String = "[Continue your last message without repeating its original content.]",
        continuePrefill: Boolean = false,
    ) {
        fun addToChatCompletion(source: String) {
            if (!prompts.has(source)) return
            val prompt = prompts.get(source) ?: return
            if (prompt.injectionPosition == PromptInjection.ABSOLUTE) return
            val collection = CompletionCollection(source)
            val countType = if (source == "bias") "bias" else "prompt"
            collection.add(
                CompletionMessage(
                    role = prompt.role,
                    content = prompt.content,
                    identifier = source,
                    tokens = handler.countAsync(prompt.content, countType),
                ),
            )
            chatCompletion.add(collection, prompts.index(source))
        }

        // 官方：每次回复都以 <|start|>assistant<|message|> 预留 3 token
        chatCompletion.reserveBudget(3)

        FIXED_ORDER.forEach(::addToChatCompletion)
        chatCompletion.setOverriddenPrompts(prompts.overriddenPrompts)

        // control prompts：impersonate + quiet（quiet 永远最后）
        val controlPrompts = CompletionCollection("controlPrompts")
        if (type == "impersonate") {
            prompts.get("impersonate")?.let {
                controlPrompts.add(
                    CompletionMessage(
                        role = it.role,
                        content = it.content,
                        identifier = "impersonate",
                        tokens = handler.countAsync(it.content, "impersonate"),
                    ),
                )
            }
        }
        prompts.get("quietPrompt")?.let {
            if (it.content.isNotEmpty()) {
                controlPrompts.add(
                    CompletionMessage(
                        role = it.role,
                        content = it.content,
                        identifier = "quietPrompt",
                        tokens = handler.countAsync(it.content, "prompt"),
                    ),
                )
            }
        }
        chatCompletion.reserveBudget(controlPrompts.getTokens())

        val systemPrompts = listOf("nsfw", "jailbreak")
        val userRelative = prompts.collection
            .filter { !it.systemPrompt && it.injectionPosition != PromptInjection.ABSOLUTE }
            .map { it.identifier }
        for (identifier in systemPrompts + userRelative) {
            addToChatCompletion(identifier)
        }

        if (prompts.has("enhanceDefinitions")) addToChatCompletion("enhanceDefinitions")
        if (prompts.get("bias")?.content?.trim()?.isNotEmpty() == true) addToChatCompletion("bias")

        // 相对扩展提示注入 main 集合（start/end）
        fun injectToMain(prompt: PromptItem) {
            if (!chatCompletion.has("main")) return
            val message = CompletionMessage(
                role = prompt.role,
                content = prompt.content,
                identifier = prompt.identifier,
                tokens = handler.countAsync(prompt.content, "prompt"),
            )
            when (prompt.position) {
                "start" -> chatCompletion.insertAtStart(message, "main")
                "end" -> chatCompletion.insertAtEnd(message, "main")
                else -> return
            }
        }
        for (key in KNOWN_RELATIVE) {
            prompts.get(key)?.takeIf { it.position != null }?.let(::injectToMain)
        }
        prompts.collection.filter { it.extension && it.position != null }.forEach(::injectToMain)

        // in-chat 深度注入（官方 populationInjectionPrompts）：按 depth 把绝对提示插进聊天
        val absolutePrompts = prompts.collection.filter { it.injectionPosition == PromptInjection.ABSOLUTE }
        val injectedMessages = injectInChat(messages, absolutePrompts, inChatExtensions)

        if (pinExamples) {
            DialogueExamplesPopulator.populate(chatCompletion, handler, prompts, messageExamples, newExampleChatPrompt, env)
            ChatHistoryPopulator.populate(
                messages = injectedMessages,
                chatCompletion = chatCompletion,
                prompts = prompts,
                handler = handler,
                type = type,
                newChatPrompt = newChatPrompt,
                env = env,
                selectedGroup = selectedGroup,
                sendIfEmpty = sendIfEmpty,
                cyclePrompt = cyclePrompt,
                continueNudgePrompt = continueNudgePrompt,
                continuePrefill = continuePrefill,
            )
        } else {
            ChatHistoryPopulator.populate(
                messages = injectedMessages,
                chatCompletion = chatCompletion,
                prompts = prompts,
                handler = handler,
                type = type,
                newChatPrompt = newChatPrompt,
                env = env,
                selectedGroup = selectedGroup,
                sendIfEmpty = sendIfEmpty,
                cyclePrompt = cyclePrompt,
                continueNudgePrompt = continueNudgePrompt,
                continuePrefill = continuePrefill,
            )
            DialogueExamplesPopulator.populate(chatCompletion, handler, prompts, messageExamples, newExampleChatPrompt, env)
        }

        chatCompletion.freeBudget(controlPrompts.getTokens())
        chatCompletion.add(controlPrompts)
    }

    /**
     * 对齐 openai.js populationInjectionPrompts：
     * 按 depth 升序、order 降序、role system→user→assistant 组装，
     * 插入到第 depth 条消息之后；order==100 时并入同 depth 的 in-chat 扩展提示。
     */
    fun injectInChat(
        messages: List<PromptMessage>,
        absolutePrompts: List<PromptItem>,
        inChatExtensions: List<ExtensionPrompt>,
    ): List<PromptMessage> {
        val result = messages.toMutableList()
        val maxDepth = maxOf(
            absolutePrompts.maxOfOrNull { it.injectionDepth ?: 0 } ?: 0,
            inChatExtensions.maxOfOrNull { it.depth } ?: 0,
        )
        var totalInserted = 0

        for (depth in 0..maxDepth) {
            val depthPrompts = absolutePrompts.filter {
                (it.injectionDepth ?: 0) == depth && it.content.isNotEmpty()
            }
            if (depthPrompts.isEmpty() && inChatExtensions.none { it.depth == depth && it.content.isNotEmpty() }) {
                continue
            }
            val roleMessages = mutableListOf<PromptMessage>()
            val orderGroups = depthPrompts.groupBy { it.injectionOrder ?: 100 }
            for (order in orderGroups.keys.sortedDescending()) {
                for (role in listOf("system", "user", "assistant")) {
                    val parts = orderGroups[order].orEmpty()
                        .filter { it.role == role }
                        .map { it.content }
                    val extParts = if (order == 100) {
                        inChatExtensions
                            .filter { it.depth == depth && it.role == role && it.content.isNotEmpty() }
                            .map { it.content }
                    } else {
                        emptyList()
                    }
                    val joint = (parts + extParts)
                        .filter { it.isNotBlank() }
                        .map { it.trim() }
                        .joinToString("\n")
                    if (joint.isNotEmpty()) {
                        roleMessages.add(
                            PromptMessage(
                                role = role,
                                content = joint,
                                position = "in_chat",
                                extension = true,
                                injected = true,
                            ),
                        )
                    }
                }
            }
            if (roleMessages.isNotEmpty()) {
                val injectIdx = depth + totalInserted
                result.addAll(injectIdx.coerceAtMost(result.size), roleMessages)
                totalInserted += roleMessages.size
            }
        }
        return result
    }
}
