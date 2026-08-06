package com.emberinn.engine.prompt

import com.emberinn.engine.macros.ChatMessage
import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv

/**
 * Instruct 模式（对照官方 public/scripts/instruct-mode.js + script.js createRawPrompt）。
 * 纯函数逐项移植；宏替换走 MacroEngine（env.user/env.char 即 name1/name2）。
 */
object InstructMode {

    private val nameMacro = Regex("""\{\{name\}\}""", RegexOption.IGNORE_CASE)
    private val startTag = Regex("""<START>""", RegexOption.IGNORE_CASE)
    private val startTagNewline = Regex("""<START>\n""", RegexOption.IGNORE_CASE)
    private val trailingSpace = Regex("""\s$""")

    /** substituteParams(text, {name1Override, name2Override})。 */
    private fun substituteWithNames(text: String, env: MacroEnv, name1: String, name2: String): String =
        MacroEngine.substitute(text, env.copy(user = name1, char = name2))

    /** substituteParams(text)（无 override）。 */
    private fun substitute(text: String, env: MacroEnv): String =
        MacroEngine.substitute(text, env)

    /**
     * 对齐 formatInstructModeChat。
     */
    fun formatChat(
        name: String,
        mes: String,
        isUser: Boolean,
        isNarrator: Boolean,
        forceAvatar: String,
        name1: String,
        name2: String,
        forceOutputSequence: ForceOutputSequence,
        instruct: InstructSettings,
        env: MacroEnv,
        selectedGroup: Boolean = false,
    ): String {
        var includeNames = if (isNarrator) false else instruct.namesBehavior == NamesBehavior.ALWAYS

        if (!isNarrator && instruct.namesBehavior == NamesBehavior.FORCE &&
            ((selectedGroup && name != name1) || (forceAvatar.isNotEmpty() && name != name1))
        ) {
            includeNames = true
        }

        fun getPrefix(): String = when {
            isNarrator -> if (instruct.systemSameAsUser) instruct.inputSequence else instruct.systemSequence
            isUser -> when (forceOutputSequence) {
                ForceOutputSequence.FIRST -> instruct.firstInputSequence.ifEmpty { instruct.inputSequence }
                ForceOutputSequence.LAST -> instruct.lastInputSequence.ifEmpty { instruct.inputSequence }
                ForceOutputSequence.NONE -> instruct.inputSequence
            }
            else -> when (forceOutputSequence) {
                ForceOutputSequence.FIRST -> instruct.firstOutputSequence.ifEmpty { instruct.outputSequence }
                ForceOutputSequence.LAST -> instruct.lastOutputSequence.ifEmpty { instruct.outputSequence }
                ForceOutputSequence.NONE -> instruct.outputSequence
            }
        }

        fun getSuffix(): String = when {
            isNarrator -> if (instruct.systemSameAsUser) instruct.inputSuffix else instruct.systemSuffix
            isUser -> instruct.inputSuffix
            else -> instruct.outputSuffix
        }

        var prefix = getPrefix()
        var suffix = getSuffix()

        if (instruct.macro) {
            prefix = substituteWithNames(prefix, env, name1, name2)
            prefix = prefix.replace(nameMacro, name.ifEmpty { "System" })
            suffix = substituteWithNames(suffix, env, name1, name2)
            suffix = suffix.replace(nameMacro, name.ifEmpty { "System" })
        }

        if (suffix.isEmpty() && instruct.wrap) suffix = "\n"
        val separator = if (instruct.wrap) "\n" else ""

        val textArray = if (includeNames && name.isNotEmpty()) {
            listOf(prefix, "$name: $mes$suffix")
        } else {
            listOf(prefix, "$mes$suffix")
        }
        return textArray.filter { it.isNotEmpty() }.joinToString(separator)
    }

    /**
     * 对齐 formatInstructModeStoryString。
     */
    fun formatStoryString(
        storyString: String,
        context: ContextSettings,
        instruct: InstructSettings,
        env: MacroEnv,
    ): String {
        if (storyString.isEmpty()) return ""

        val applySequences = context.storyStringPosition != StoryStringPosition.IN_CHAT
        val separator = if (instruct.wrap) "\n" else ""
        var out = storyString

        if (applySequences && instruct.storyStringPrefix.isNotEmpty()) {
            val prefix = substitute(instruct.storyStringPrefix, env)
                .replace(nameMacro, "System")
            out = prefix + separator + out
        }

        if (applySequences && instruct.storyStringSuffix.isNotEmpty()) {
            out += substitute(instruct.storyStringSuffix, env)
        }

        return out
    }

    /**
     * 对齐 formatInstructModeExamples（含 openai.js parseExampleIntoIndividual）。
     */
    fun formatExamples(
        mesExamplesArray: List<String>,
        name1: String,
        name2: String,
        context: ContextSettings,
        instruct: InstructSettings,
        env: MacroEnv,
        selectedGroup: Boolean = false,
        groupBotNames: List<String> = emptyList(),
    ): List<String> {
        val blockHeading = if (context.exampleSeparator.isNotEmpty()) {
            substitute(context.exampleSeparator, env) + "\n"
        } else {
            ""
        }

        if (instruct.skipExamples) {
            return mesExamplesArray.map { it.replaceFirst(startTagNewline, blockHeading) }
        }

        val includeNames = instruct.namesBehavior == NamesBehavior.ALWAYS
        val includeGroupNames = selectedGroup &&
            (instruct.namesBehavior == NamesBehavior.ALWAYS || instruct.namesBehavior == NamesBehavior.FORCE)

        var inputPrefix = instruct.inputSequence
        var outputPrefix = instruct.outputSequence
        var inputSuffix = instruct.inputSuffix
        var outputSuffix = instruct.outputSuffix

        if (instruct.macro) {
            inputPrefix = substituteWithNames(inputPrefix, env, name1, name2).replace(nameMacro, name1)
            outputPrefix = substituteWithNames(outputPrefix, env, name1, name2).replace(nameMacro, name2)
            inputSuffix = substituteWithNames(inputSuffix, env, name1, name2).replace(nameMacro, name1)
            outputSuffix = substituteWithNames(outputSuffix, env, name1, name2).replace(nameMacro, name2)

            if (inputSuffix.isEmpty() && instruct.wrap) inputSuffix = "\n"
            if (outputSuffix.isEmpty() && instruct.wrap) outputSuffix = "\n"
        }

        val separator = if (instruct.wrap) "\n" else ""
        val formattedExamples = mutableListOf<String>()

        for (item in mesExamplesArray) {
            val cleanedItem = item.replaceFirst(startTag, "{Example Dialogue:}").replace("\r", "")
            val blockExamples = ExampleParser.parse(
                cleanedItem,
                appendNamesForGroup = includeGroupNames,
                selectedGroup = selectedGroup,
                name1 = name1,
                name2 = name2,
                groupBotNames = groupBotNames,
            )
            if (blockExamples.isEmpty()) continue

            if (blockHeading.isNotEmpty()) formattedExamples.add(blockHeading)

            for (example in blockExamples) {
                val isUserExample = example.name == "example_user"
                val includeThisName = !includeGroupNames &&
                    (includeNames || (instruct.namesBehavior == NamesBehavior.FORCE && isUserExample))

                val prefix = if (isUserExample) inputPrefix else outputPrefix
                val suffix = if (isUserExample) inputSuffix else outputSuffix
                val name = if (isUserExample) name1 else name2
                val messageContent = if (includeThisName) "$name: ${example.content}" else example.content
                val formattedMessage = listOf(prefix, messageContent + suffix)
                    .filter { it.isNotEmpty() }
                    .joinToString(separator)
                formattedExamples.add(formattedMessage)
            }
        }

        if (formattedExamples.isEmpty()) {
            return mesExamplesArray.map { it.replaceFirst(startTagNewline, blockHeading) }
        }
        return formattedExamples
    }

    /**
     * 对齐 formatInstructModePrompt。
     */
    fun formatPrompt(
        name: String,
        isImpersonate: Boolean,
        promptBias: String,
        name1: String,
        name2: String,
        isQuiet: Boolean,
        isQuietToLoud: Boolean,
        instruct: InstructSettings,
        env: MacroEnv,
        selectedGroup: Boolean = false,
    ): String {
        val includeNames = name.isNotEmpty() &&
            (instruct.namesBehavior == NamesBehavior.ALWAYS ||
                (selectedGroup && instruct.namesBehavior == NamesBehavior.FORCE)) &&
            !(isQuiet && !isQuietToLoud)

        fun getSequence(): String = when {
            isImpersonate -> instruct.lastInputSequence.ifEmpty { instruct.inputSequence }
            isQuiet && !isQuietToLoud -> instruct.lastSystemSequence.ifEmpty { instruct.outputSequence }
            isQuiet && isQuietToLoud -> instruct.lastOutputSequence.ifEmpty { instruct.outputSequence }
            else -> instruct.lastOutputSequence.ifEmpty { instruct.outputSequence }
        }

        var sequence = getSequence()
        var nameFiller = ""

        // Mistral 格式 hack：output 以空格结尾而 last_output 不以空格结尾时，补一个空格
        if (
            includeNames &&
            instruct.lastOutputSequence.isNotEmpty() &&
            instruct.outputSequence.isNotEmpty() &&
            sequence == instruct.lastOutputSequence &&
            trailingSpace.containsMatchIn(instruct.outputSequence) &&
            !trailingSpace.containsMatchIn(instruct.lastOutputSequence)
        ) {
            nameFiller = instruct.outputSequence.takeLast(1)
        }

        if (instruct.macro) {
            sequence = substituteWithNames(sequence, env, name1, name2)
                .replace(nameMacro, name.ifEmpty { "System" })
        }

        val separator = if (instruct.wrap) "\n" else ""
        var text = if (includeNames) {
            separator + sequence + separator + nameFiller + "$name:"
        } else {
            separator + sequence
        }

        // Quiet prompt 已自带结尾换行
        if (isQuiet && separator.isNotEmpty()) {
            text = text.removePrefix(separator)
        }

        if (!isImpersonate && promptBias.isNotEmpty()) {
            text += if (includeNames) promptBias else separator + promptBias.trimStart()
        }

        return (if (instruct.wrap) text.trimEnd() else text) + (if (includeNames) "" else separator)
    }

    /**
     * 对齐 getInstructStoppingSequences。
     */
    fun stoppingSequences(
        name1: String,
        name2: String,
        instruct: InstructSettings,
        context: ContextSettings,
        env: MacroEnv,
        customInstruct: InstructSettings? = null,
        useStopStrings: Boolean? = null,
    ): List<String> {
        val settings = customInstruct ?: instruct
        val result = mutableListOf<String>()

        fun addInstructSequence(sequence: String) {
            val wrap = { s: String -> if (settings.wrap) "\n$s" else s }
            if (sequence.isNotEmpty() && sequence.trim().isNotEmpty()) {
                val wrappedSequence = wrap(sequence)
                val stopString = if (settings.macro) substitute(wrappedSequence, env) else wrappedSequence
                result.add(stopString)
            }
        }

        if (customInstruct != null || settings.enabled) {
            val stopSequence = settings.stopSequence
            val inputSequence = settings.inputSequence.replace(nameMacro, name1)
            val outputSequence = settings.outputSequence.replace(nameMacro, name2)
            val firstOutputSequence = settings.firstOutputSequence.replace(nameMacro, name2)
            val lastOutputSequence = settings.lastOutputSequence.replace(nameMacro, name2)
            val systemSequence = settings.systemSequence.replace(nameMacro, "System")
            val lastSystemSequence = settings.lastSystemSequence.replace(nameMacro, "System")

            val combined = mutableListOf(stopSequence)
            if (settings.sequencesAsStopStrings) {
                combined += inputSequence
                combined += outputSequence
                combined += firstOutputSequence
                combined += lastOutputSequence
                combined += systemSequence
                combined += lastSystemSequence
            }

            combined.joinToString("\n").split("\n").distinct().forEach(::addInstructSequence)
        }

        if (useStopStrings ?: context.useStopStrings) {
            if (context.chatStart.isNotEmpty()) {
                result.add("\n" + substitute(context.chatStart, env))
            }
            if (context.exampleSeparator.isNotEmpty()) {
                result.add("\n" + substitute(context.exampleSeparator, env))
            }
        }

        return result
    }

    /**
     * 对齐 script.js formatMessageHistoryItem（Text Completion 历史行）。
     */
    fun formatHistoryItem(
        chatItem: ChatMessage,
        isInstruct: Boolean,
        forceOutputSequence: ForceOutputSequence,
        name1: String,
        name2: String,
        instruct: InstructSettings,
        env: MacroEnv,
        selectedGroup: Boolean = false,
        isNarrator: Boolean = false,
        forceAvatar: String = "",
        ignore: Boolean = false,
    ): String {
        if (ignore) return ""

        val characterName = chatItem.name?.takeIf { it.isNotEmpty() } ?: name2
        val itemName = if (chatItem.isUser) chatItem.name ?: "" else characterName
        val shouldPrependName = !isNarrator

        var textResult = if (chatItem.name?.isNotEmpty() == true && shouldPrependName) {
            "$itemName: ${chatItem.mes}\n"
        } else {
            "${chatItem.mes}\n"
        }

        if (isInstruct) {
            textResult = formatChat(
                name = itemName,
                mes = chatItem.mes,
                isUser = chatItem.isUser,
                isNarrator = isNarrator,
                forceAvatar = forceAvatar,
                name1 = name1,
                name2 = name2,
                forceOutputSequence = forceOutputSequence,
                instruct = instruct,
                env = env,
                selectedGroup = selectedGroup,
            )
        }

        return textResult
    }

    /** createRawPrompt 返回类型：Chat Completion = 消息列表，Text Completion = 字符串。 */
    sealed class RawPrompt {
        data class Messages(val messages: List<PromptMessage>) : RawPrompt()
        data class Text(val text: String) : RawPrompt()
    }

    /**
     * 对齐 script.js createRawPrompt。
     * prompt 为空且无 systemPrompt 时抛错（对齐官方）。
     */
    fun createRawPrompt(
        prompt: List<PromptMessage>,
        api: String,
        instructOverride: Boolean,
        quietToLoud: Boolean,
        systemPrompt: String,
        prefill: String,
        instruct: InstructSettings,
        context: ContextSettings,
        env: MacroEnv,
        name1: String = env.user,
        name2: String = env.char,
    ): RawPrompt {
        val isInstruct = instruct.enabled && api != "openai" && api != "novel" && !instructOverride

        if (prompt.isEmpty() && systemPrompt.isEmpty()) error("No messages provided")

        val resolvedPrefill = substitute(prefill, env)
        val messages = prompt.map { m ->
            val name = when (m.role) {
                "user" -> m.name?.takeIf { it.isNotEmpty() } ?: name1
                "assistant" -> m.name?.takeIf { it.isNotEmpty() } ?: name2
                else -> m.name ?: ""
            }
            val prefix = if (isInstruct || api == "openai") {
                ""
            } else if (name.isNotEmpty()) {
                "$name: "
            } else {
                ""
            }
            var content = prefix + substitute(m.content, env)
            if (isInstruct) {
                val isUser = m.role == "user"
                val isNarrator = m.role == "system"
                content = formatChat(
                    name = name,
                    mes = content,
                    isUser = isUser,
                    isNarrator = isNarrator,
                    forceAvatar = "",
                    name1 = name1,
                    name2 = name2,
                    forceOutputSequence = ForceOutputSequence.NONE,
                    instruct = instruct,
                    env = env,
                )
            }
            m.copy(content = content)
        }.toMutableList()

        if (systemPrompt.isNotEmpty()) {
            var resolvedSystem = substitute(systemPrompt, env)
            resolvedSystem = if (isInstruct) {
                formatStoryString(resolvedSystem, context, instruct, env)
            } else {
                resolvedSystem.trim()
            }
            if (
                isInstruct &&
                resolvedSystem.isNotEmpty() &&
                !resolvedSystem.endsWith("\n") &&
                instruct.wrap &&
                instruct.storyStringSuffix.isEmpty()
            ) {
                resolvedSystem += "\n"
            }
            messages.add(0, PromptMessage("system", resolvedSystem))
        }

        if (api == "openai" && resolvedPrefill.isNotEmpty()) {
            messages.add(PromptMessage("assistant", resolvedPrefill))
        }

        if (api != "openai") {
            val joiner = if (isInstruct) "" else "\n"
            var text = messages.joinToString(joiner) { it.content }
            text = if (api == "novel") adjustNovelInstructionPrompt(text) else text
            text += if (isInstruct) {
                formatPrompt(
                    name = name2,
                    isImpersonate = false,
                    promptBias = resolvedPrefill,
                    name1 = name1,
                    name2 = name2,
                    isQuiet = true,
                    isQuietToLoud = quietToLoud,
                    instruct = instruct,
                    env = env,
                )
            } else {
                "\n$resolvedPrefill"
            }
            return RawPrompt.Text(text)
        }

        return RawPrompt.Messages(messages)
    }

    /** 对齐 nai-settings.js adjustNovelInstructionPrompt。 */
    fun adjustNovelInstructionPrompt(prompt: String): String {
        val stripped = prompt.replace(Regex("""[\[\]]"""), "").trim()
        return if (!stripped.contains("{ ")) "{ $stripped }" else stripped
    }
}

/** 对齐 openai.js parseExampleIntoIndividual（示例块 → 单条消息）。 */
object ExampleParser {

    fun parse(
        messageExampleString: String,
        appendNamesForGroup: Boolean,
        selectedGroup: Boolean,
        name1: String,
        name2: String,
        groupBotNames: List<String> = emptyList(),
    ): List<ExampleMessage> {
        val groupBotLines = groupBotNames.map { "$it:" }
        val lines = messageExampleString.split("\n")
        val result = mutableListOf<ExampleMessage>()
        val curMsgLines = mutableListOf<String>()
        var inUser = false
        var inBot = false
        var botName = name2

        fun addMsg(name: String, systemName: String) {
            var parsedMsg = curMsgLines.joinToString("\n")
                .replaceFirst(name + ":", "")
                .trim()
            if (appendNamesForGroup && selectedGroup &&
                (systemName == "example_user" || systemName == "example_assistant")
            ) {
                parsedMsg = "$name: $parsedMsg"
            }
            result.add(ExampleMessage(name = systemName, content = parsedMsg))
            curMsgLines.clear()
        }

        // 跳过第一行（“This is how {bot name} should talk”）
        for (i in 1 until lines.size) {
            val curStr = lines[i]
            if (curStr.startsWith("$name1:")) {
                inUser = true
                if (inBot) addMsg(botName, "example_assistant")
                inBot = false
            } else if (curStr.startsWith("$name2:") || groupBotLines.any { curStr.startsWith(it) }) {
                if (!curStr.startsWith("$name2:") && groupBotLines.isNotEmpty()) {
                    botName = curStr.split(":")[0]
                }
                inBot = true
                if (inUser) addMsg(name1, "example_user")
                inUser = false
            }
            curMsgLines.add(curStr)
        }

        if (inUser) {
            addMsg(name1, "example_user")
        } else if (inBot) {
            addMsg(botName, "example_assistant")
        }

        return result
    }
}
