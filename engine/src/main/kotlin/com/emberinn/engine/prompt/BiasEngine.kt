package com.emberinn.engine.prompt

/**
 * 官方 script.js extractMessageBias / getBiasStrings / removeMacros 的引擎移植。
 * extractMessageBias 用轻量 Handlebars 兼容解析：只支持 `{{bias 参数}}` 简单 helper，
 * 取第一个参数（支持单/双引号），与官方 registerHelper('bias', function(text){...}) 语义一致。
 */
data class BiasChatMessage(
    val isUser: Boolean = false,
    val isSystem: Boolean = false,
    val isNarrator: Boolean = false,
    val bias: String? = null,
)

data class BiasConfig(
    val userPromptBias: String = "",
    val chat: List<BiasChatMessage> = emptyList(),
    val substitute: (String) -> String = { it },
)

data class BiasResult(
    val messageBias: String = "",
    val promptBias: String = "",
    val isUserPromptBias: Boolean = false,
)

object BiasEngine {

    private val biasRegex = Regex("""\{\{\s*bias(?:\s+([\s\S]*?))?\s*\}\}""", RegexOption.IGNORE_CASE)
    private val legacyBiasColonRegex = Regex("""\{\{\s*bias\s*:([\s\S]*?)\s*\}\}""", RegexOption.IGNORE_CASE)

    fun extractMessageBias(message: String): String {
        if (message.isEmpty()) return ""
        val matches = mutableListOf<String>()
        var last = 0
        val sb = StringBuilder()
        for (m in biasRegex.findAll(message)) {
            sb.append(message, last, m.range.first)
            val argText = m.groupValues[1]
            if (argText.isBlank()) {
                // {{bias}} 无参调用时官方 helper 收到 options 对象，push 后 join 成 "[object Object]"
                matches += "[object Object]"
            } else {
                // 非引号参数在 Handlebars 里是路径表达式，未定义时为 undefined；
                // 官方 push(undefined) 后 join 表现为空串，因此这里只保留引号字面量。
                matches += parseArgs(argText)
            }
            last = m.range.last + 1
        }
        sb.append(message, last, message.length)
        // App 历史兼容：{{bias:...}} 冒号写法（官方 Handlebars 不支持，作为 README 扩展保留）
        val colonText = sb.toString()
        sb.setLength(0)
        var colonLast = 0
        for (m in legacyBiasColonRegex.findAll(colonText)) {
            sb.append(colonText, colonLast, m.range.first)
            val value = m.groupValues[1].trim()
            if (value.isNotEmpty()) matches += value
            colonLast = m.range.last + 1
        }
        sb.append(colonText, colonLast, colonText.length)
        // 官方 helper 返回 ''，模板渲染后 bias 调用点被移除；这里返回偏置串即可，
        // 调用方需要“移除后文本”时用 removeMacros(rendered)（官方 sendMessageAsUser 语义）。
        return if (matches.isEmpty()) "" else " " + matches.joinToString(" ")
    }

    fun getBiasStrings(
        textareaText: String,
        type: String,
        config: BiasConfig,
    ): BiasResult {
        if (type == "impersonate" || type == "continue") {
            return BiasResult()
        }

        var promptBias = ""
        val messageBias = extractMessageBias(textareaText)

        // 用户输入为空时，回溯最后一条相关消息的 extra.bias（swipe 跳过最后一条）
        if (textareaText.isEmpty()) {
            for (i in config.chat.indices.reversed()) {
                val mes = config.chat[i]
                if (type == "swipe" && config.chat.lastIndex == i) continue
                if (mes.isUser || mes.isSystem || mes.isNarrator) {
                    val bias = mes.bias?.trim()
                    if (!bias.isNullOrEmpty()) {
                        promptBias = bias
                    }
                    break
                }
            }
        }

        val resolvedPromptBias = messageBias.ifEmpty { promptBias }.ifEmpty { config.userPromptBias }
        val isUserPromptBias = resolvedPromptBias == config.userPromptBias

        return BiasResult(
            messageBias = config.substitute(messageBias),
            promptBias = config.substitute(resolvedPromptBias),
            isUserPromptBias = isUserPromptBias,
        )
    }

    /** 官方 script.js removeMacros：删掉所有 {{...}} 并 trim。 */
    fun removeMacros(str: String?): String =
        (str ?: "").replace(Regex("""\{\{[\s\S]*?\}\}"""), "").trim()

    private fun parseArgs(raw: String): List<String> {
        val args = mutableListOf<String>()
        var i = 0
        while (i < raw.length) {
            while (i < raw.length && raw[i].isWhitespace()) i++
            if (i >= raw.length) break
            val c = raw[i]
            if (c == '"' || c == '\'') {
                val close = raw.indexOf(c, i + 1)
                if (close < 0) {
                    args += raw.substring(i + 1)
                    break
                }
                args += raw.substring(i + 1, close)
                i = close + 1
            } else {
                var end = i
                while (end < raw.length && !raw[end].isWhitespace()) end++
                // 官方把未定义路径当 undefined 传给 helper，join 时是空串
                args += ""
                i = end
            }
        }
        return args
    }
}
