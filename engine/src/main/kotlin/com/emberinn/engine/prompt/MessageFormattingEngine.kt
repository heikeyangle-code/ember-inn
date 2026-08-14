package com.emberinn.engine.prompt

/**
 * 消息显示格式化引擎（官方 script.js messageFormatting 的纯文本子集，差分锁死）。
 *
 * 移植范围（官方 release 8172dcd，script.js:1753-1905）：
 *   1. messageId===0 且非系统/非用户/非推理 → substituteParams（macroSubstitute 注入）
 *   2. Note 评论 / 非 systemUserName 的 is_system 消息 → 按普通消息格式化（script.js:1770-1776）
 *   3. user_prompt_bias 前缀剥离（show_user_prompt_bias=关）
 *   4. 显示位点正则（getRegexedString isMarkdown=true；regexApply 注入，位点/深度本引擎计算）
 *   5. auto_fix_generated_markdown → fixMarkdown(forDisplay=true)
 *   6. encode_tags（< 全转义；行首/换行+空白后的 > 保留，官方负向后顾等价）
 *   7. reasoning prefix/suffix 首处转义（官方 utils.js escapeHtml）
 *   8. 非系统消息收尾 mes.trim()（官方在 makeHtml 后执行；本移植在纯文本上执行，差分脚本同边界）
 *   9. allow_name2_display=关 → 剥“角色名:”前缀
 *   10. messageId==0 时把宏替换结果随返回值带出（firstMessageSubstituted），由 App 按官方写回 chat.mes
 *
 * 边界登记（不移植）：
 *   - 引号对转换 / Showdown makeHtml / DOMPurify / encodeStyleTags：属渲染器边界，
 *     官方在 step 8 之前执行；App 渲染管线（preprocessOfficialHtml / WebView）承担。
 *   - 官方 name2 前缀剥离在 makeHtml 之后（HTML 文本上）；本移植在纯文本上执行，
 *     语义等价（不匹配 markdown 语法已转换的 HTML 结构），差分脚本同边界。
 */
object MessageFormattingEngine {

    /** 官方 regex_placement（extensions/regex/engine.js:281-290）。 */
    object RegexPlacement {
        const val USER_INPUT = 1
        const val AI_OUTPUT = 2
        const val SLASH_COMMAND = 3
        const val WORLD_INFO = 5
        const val REASONING = 6
    }

    /** 官方 script.js encode_tags（power_user.encode_tags；< 全转义，行首 > 保留）。 */
    fun encodeTags(text: String): String {
        val sb = StringBuilder(text.length)
        var lineStart = true
        for (c in text) {
            when {
                c == '<' -> { sb.append("&lt;"); lineStart = false }
                c == '>' -> { sb.append(if (lineStart) ">" else "&gt;"); lineStart = false }
                c == '\n' -> { sb.append('\n'); lineStart = true }
                else -> { sb.append(c); if (!c.isWhitespace()) lineStart = false }
            }
        }
        return sb.toString()
    }

    /** 官方 utils.js escapeHtml（reasoning 前后缀转义）。 */
    fun escapeHtml(text: String): String = buildString {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    /** 官方 utils.js escapeRegex（逐字符转义集一致，保证 name2 前缀匹配）。 */
    fun escapeRegex(text: String): String = buildString {
        for (c in text) {
            if (c in """.^$*+?()[]{}\|/\-""") append('\\')
            append(c)
        }
    }

    /**
     * 官方 messageFormatting 纯文本子集。
     *
     * @param depth 官方 usableMessages.length - indexOf - 1（messageId>=0 时）；推理/缺失传 null。
     * @param macroSubstitute 官方 substituteParams（第一条消息与 bias 前缀用；App 注入 MacroEngine）。
     * @param regexApply 官方 getRegexedString（isMarkdown=true；App 注入 RegexPipelineEngine，禁用时传恒等）。
     */
    fun format(
        mes: String,
        chName: String?,
        isSystem: Boolean,
        isUser: Boolean,
        isNarrator: Boolean,
        messageId: Int,
        isReasoning: Boolean = false,
        settings: MessageFormattingSettings = MessageFormattingSettings(),
        depth: Int? = null,
        macroSubstitute: (String) -> String = { it },
        regexApply: (String, Int, Int?) -> String = { text, _, _ -> text },
    ): MessageFormattingResult {
        if (mes.isEmpty()) return MessageFormattingResult("")

        var out = mes
        var firstMessageSubstituted: String? = null
        // 官方 messageFormatting：messageId===0 的第一条 AI 消息先 substituteParams
        if (messageId == 0 && !isSystem && !isUser && !isReasoning) {
            out = macroSubstitute(out)
            firstMessageSubstituted = out
        }

        // 官方：Note 评论 / 非 systemUserName 的系统消息按普通消息格式化（script.js:1770-1776）
        var effectiveSystem = isSystem
        if (chName == settings.commentName && isSystem && !isUser) effectiveSystem = false
        if (effectiveSystem && chName != settings.systemUserName) effectiveSystem = false

        // 官方：user_prompt_bias 前缀剥离（show_user_prompt_bias=关；替换后非空才剥）
        val bias = settings.userPromptBias
        val replacedBias = if (bias.isNotEmpty()) macroSubstitute(bias) else ""
        if (replacedBias.isNotEmpty() && !settings.showUserPromptBias && !chName.isNullOrEmpty() && !isUser && !effectiveSystem &&
            out.startsWith(replacedBias)
        ) {
            out = out.substring(replacedBias.length)
        }

        // 官方：显示位点正则（isMarkdown=true，depth 由调用方按官方公式预计算）
        if (!effectiveSystem) {
            val placement = when {
                isReasoning -> RegexPlacement.REASONING
                isUser -> RegexPlacement.USER_INPUT
                isNarrator -> RegexPlacement.SLASH_COMMAND
                else -> RegexPlacement.AI_OUTPUT
            }
            out = regexApply(out, placement, depth)
        }

        // 官方：auto_fix_generated_markdown（默认开）
        if (settings.autoFixMarkdown) out = FixMarkdown.fix(out, forDisplay = true)

        // 官方：encode_tags（仅非系统）
        if (!effectiveSystem && settings.encodeTags) out = encodeTags(out)

        // 官方：reasoning 前后缀首处转义（escapeHtml）
        for (reasoningString in listOf(settings.reasoningPrefix, settings.reasoningSuffix)) {
            if (reasoningString.isBlank()) continue
            if (out.contains(reasoningString)) {
                out = out.replaceFirst(reasoningString, escapeHtml(reasoningString))
            }
        }

        // 官方：非系统消息 makeHtml 后 trim（本移植在纯文本上执行，差分脚本同边界）
        if (!effectiveSystem) out = out.trim()

        // 官方：allow_name2_display=关 → 剥 AI 正文“角色名:”前缀（边界：官方在 makeHtml 后执行）
        if (!settings.allowName2Display && !chName.isNullOrEmpty() && !isUser && !effectiveSystem) {
            out = Regex("(^|\n)" + escapeRegex(chName) + ":").replace(out, "$1")
        }

        return MessageFormattingResult(out, firstMessageSubstituted)
    }
}

/** 官方 messageFormatting 结果：text=显示文本；firstMessageSubstituted=messageId==0 时宏替换结果（App 按官方写回 chat.mes）。 */
data class MessageFormattingResult(
    val text: String,
    val firstMessageSubstituted: String? = null,
)

/** 官方 messageFormatting 相关 power-user 设置（默认值与官方一致）。 */
data class MessageFormattingSettings(
    val userPromptBias: String = "",
    val showUserPromptBias: Boolean = true,
    val autoFixMarkdown: Boolean = false,
    val encodeTags: Boolean = false,
    val reasoningPrefix: String = "",
    val reasoningSuffix: String = "",
    val allowName2Display: Boolean = false,
    val commentName: String = "Note",
    val systemUserName: String = "SillyTavern System",
)
