package com.emberinn.engine.prompt

import com.emberinn.engine.worldinfo.VectorTextUtils

/**
 * 官方 script.js cleanUpMessage / cleanGroupMessage 的引擎移植。
 *
 * 依赖注入说明：
 * - userPromptBias 由调用方先做宏替换（官方 substituteParams）
 * - regexTransform 对应官方 getRegexedString（App 接 RegexPipelineEngine；差分用恒等函数隔离）
 * - stoppingStrings 对应官方 getStoppingStrings（调用方按 API/Instruct 预计算）
 * - trimToEndSentence 复用已差分的 VectorTextUtils
 */
data class CleanUpConfig(
    val userPromptBias: String? = null,
    val isImpersonate: Boolean = false,
    val isContinue: Boolean = false,
    val displayIncompleteSentences: Boolean = false,
    val stoppingStrings: List<String> = emptyList(),
    val includeUserPromptBias: Boolean = true,
    val trimNames: Boolean = true,
    val trimWrongNames: Boolean = true,
    val collapseNewlines: Boolean = false,
    val allowName1Display: Boolean = true,
    val allowName2Display: Boolean = true,
    val name1: String = "",
    val name2: String = "",
    val isInstruct: Boolean = false,
    val instructStopSequence: String = "",
    val instructInputSequence: String = "",
    val instructOutputSequence: String = "",
    val instructLastOutputSequence: String = "",
    val instructSequencesAsStopStrings: Boolean = false,
    val autoFixMarkdown: Boolean = false,
    val trimSentences: Boolean = false,
    val trimSpaces: Boolean = false,
    val hasReasoningPrefix: Boolean = false,
    val groupMemberNames: List<String> = emptyList(),
    val groupTrimmingEnabled: Boolean = true,
)

object CleanUpMessageEngine {

    fun clean(
        getMessage: String,
        config: CleanUpConfig,
        regexTransform: (String) -> String = { it },
    ): String {
        var message = getMessage
        if (message.isEmpty()) {
            return ""
        }

        // 官方：先加 prompt bias（非冒充/非继续）
        val bias = config.userPromptBias
        if (
            config.includeUserPromptBias &&
            !bias.isNullOrEmpty() &&
            !config.isImpersonate &&
            !config.isContinue
        ) {
            message = bias + message
        }

        // 官方：按 stopping string 前缀逐字符裁剪（从后往前）
        for (stoppingString in config.stoppingStrings) {
            if (stoppingString.isEmpty()) continue
            for (j in stoppingString.length downTo 1) {
                if (message.takeLast(j) == stoppingString.take(j)) {
                    message = message.dropLast(j)
                    break
                }
            }
        }

        // 官方 getRegexedString（AI_OUTPUT / USER_INPUT 由调用方注入）
        message = regexTransform(message)

        if (config.collapseNewlines) {
            message = message.replace(Regex("\\n+"), "\n")
        }

        // 官方：去掉每行行尾不可见空白（保留 \r\n）
        message = message.replace(Regex("[^\\S\\r\\n]+$", RegexOption.MULTILINE), "")

        if (config.trimWrongNames) {
            // 冒充时错误名字是角色（name2）；非冒充时是用户（name1）
            val wrongName = if (config.isImpersonate) {
                if (!config.allowName2Display) config.name2 else ""
            } else {
                if (!config.allowName1Display) config.name1 else ""
            }
            if (wrongName.isNotEmpty()) {
                if (message.startsWith("$wrongName:")) {
                    message = ""
                }
                val startIndex = message.indexOf("\n$wrongName:")
                if (startIndex >= 0) {
                    message = message.substring(0, startIndex)
                }
            }
        }

        if (message.contains("<|endoftext|>")) {
            message = message.substring(0, message.indexOf("<|endoftext|>"))
        }

        if (config.isInstruct && config.instructStopSequence.isNotEmpty()) {
            if (message.contains(config.instructStopSequence)) {
                message = message.substring(0, message.indexOf(config.instructStopSequence))
            }
        }

        // Hana：只保留 input_sequence 之前的内容
        if (config.isInstruct && config.instructInputSequence.isNotEmpty()) {
            if (message.contains(config.instructInputSequence)) {
                message = message.substring(0, message.indexOf(config.instructInputSequence))
            }
        }

        if (config.isInstruct && config.instructSequencesAsStopStrings) {
            val sequences = listOf(
                Triple(config.instructInputSequence, config.isImpersonate, config.instructInputSequence.isNotEmpty()),
                Triple(config.instructOutputSequence, !config.isImpersonate, config.instructOutputSequence.isNotEmpty()),
                Triple(config.instructLastOutputSequence, !config.isImpersonate, config.instructLastOutputSequence.isNotEmpty()),
            )
            for ((value, apply, nonEmpty) in sequences) {
                if (!apply || !nonEmpty) continue
                for (line in value.split('\n').filter { it.trim().isNotEmpty() }) {
                    message = message.replace(line, "")
                }
            }
        }

        if (config.groupMemberNames.isNotEmpty()) {
            message = cleanGroupMessage(
                getMessage = message,
                memberNames = config.groupMemberNames,
                currentSpeakerName = config.name2,
                trimmingEnabled = config.groupTrimmingEnabled,
            )
        }

        if (!config.allowName2Display && config.name2.isNotEmpty()) {
            val name2Escaped = escapeRegex(config.name2)
            message = message.replace(Regex("(^|\n)${name2Escaped}:\\s*"), "$1")
        }

        if (config.isImpersonate) {
            message = message.trim()
        }

        if (config.autoFixMarkdown) {
            message = FixMarkdown.fix(message, forDisplay = false)
        }

        if (config.trimNames) {
            val nameToTrim = if (config.isImpersonate) {
                if (!config.allowName1Display) config.name1 else ""
            } else {
                if (!config.allowName2Display) config.name2 else ""
            }
            if (nameToTrim.isNotEmpty() && message.startsWith("$nameToTrim:")) {
                message = message.removePrefix("$nameToTrim:").trimStart()
            }
        }

        if (config.isImpersonate) {
            message = message.trim()
        }

        if (!config.displayIncompleteSentences && config.trimSentences) {
            message = VectorTextUtils.trimToEndSentence(message)
        }

        if (config.trimSpaces && !config.hasReasoningPrefix) {
            message = message.trim()
        }

        return message
    }

    /**
     * 官方 script.js cleanGroupMessage：把群成员误当“当前说话人”生成的台词行裁掉。
     */
    fun cleanGroupMessage(
        getMessage: String,
        memberNames: List<String>,
        currentSpeakerName: String,
        trimmingEnabled: Boolean,
    ): String {
        if (!trimmingEnabled) {
            return getMessage
        }
        var message = getMessage
        for (name in memberNames) {
            if (name == currentSpeakerName) continue
            val regex = Regex("(^|\n)${escapeRegex(name)}:")
            val match = regex.find(message) ?: continue
            message = message.substring(0, match.range.first)
        }
        return message
    }

    /** 官方 utils.js escapeRegex。 */
    fun escapeRegex(string: String): String = Regex.escape(string)
}
