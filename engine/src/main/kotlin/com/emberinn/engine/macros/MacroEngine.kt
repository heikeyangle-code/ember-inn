package com.emberinn.engine.macros

import com.emberinn.engine.prompt.ContextSettings
import com.emberinn.engine.prompt.InstructSettings
import com.emberinn.engine.worldinfo.StringHash
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.random.Random

/** 角色卡字段（对齐官方 MacroEnv.character）。 */
data class CharacterFields(
    val charPrompt: String = "",
    val charInstruction: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val persona: String = "",
    val mesExamplesRaw: String = "",
    val charDepthPrompt: String = "",
    val creatorNotes: String = "",
    val firstMessage: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val version: String = "",
)

/** 系统字段（对齐官方 MacroEnv.system）。 */
data class SystemFields(val model: String = "")

/** 聊天消息（对齐官方 chat 消息核心字段）。 */
data class ChatMessage(
    val mes: String = "",
    val isUser: Boolean = false,
    val isSystem: Boolean = false,
    val name: String? = null,
    val swipes: List<String> = emptyList(),
    val swipeId: Int = 0,
    /** 官方 coreChat.map 追加的 append_title/媒体标题。 */
    val titles: List<String> = emptyList(),
    /** 官方 setOpenAIMessages 的 invocations（extra.tool_invocations → 工具调用链重构）。 */
    val toolInvocations: List<com.emberinn.engine.prompt.ToolInvocation>? = null,
)

/** 宏环境：对齐 MacroEnv 的核心字段。 */
data class MacroEnv(
    val user: String,
    val char: String,
    val group: String = "",
    val groupNotMuted: String = "",
    val notChar: String = "",
    val character: CharacterFields = CharacterFields(),
    val system: SystemFields = SystemFields(),
    val chat: List<ChatMessage> = emptyList(),
    val maxContextTokens: Int = 0,
    val maxResponseTokens: Int = 0,
    val maxPromptTokens: Int = 0,
    val input: String = "",
    val lastGenerationType: String = "",
    val original: String = "",
    val slash: SlashMacroState? = null,
    val firstIncludedMessageId: Int? = null,
    val firstDisplayedMessageId: Int? = null,
    val extensions: Set<String> = emptySet(),
    val isMobile: Boolean = false,
    val chatId: Long = 0,
    val chatIdHash: Long = 0,
    val rerollSeed: Long? = null,
    val contentHash: Long = 0,
    val instruct: InstructSettings? = null,
    val context: ContextSettings? = null,
    val systemPromptContent: String = "",
    val systemPromptEnabled: Boolean = true,
    val preferCharacterPrompt: Boolean = true,
    val local: VariableStore = EmptyVariableStore,
    val global: VariableStore = EmptyVariableStore,
    /** 世界书 outlet 提示（官方 {{outlet::key}} ← extension_prompts[customWIOutlet_key]）。 */
    val outlets: Map<String, String> = emptyMap(),
    /** 官方 memory 扩展 {{summary}}：返回当前聊天最新记忆摘要。 */
    val summary: String = "",
)

/**
 * 核心宏引擎（Stage 2）：
 * {{user}} {{char}} {{time}} {{date}} {{weekday}} {{isotime}} {{isodate}}
 * {{random::a::b}} {{roll::NdM}} {{pick::a::b}}
 * {{if 条件}}then{{else}}other{{/if}}（嵌套/取反/falsy/顶层 else/只解析选中分支，对齐官方 core-macros.js if 宏）
 * 未实现的宏原样保留；变量宏（getvar 等）与 seedrandom 逐位一致属 Stage 3。
 */
object MacroEngine {

    private val closingIfRegex = Regex("""\{\{/if\}\}""")
    private val openingIfScan = Regex("""\{\{if(?:\s|::)""")
    private val elseRegex = Regex("""\{\{else(?:::[^{}]*)?\}\}""")

    private val falsyValues = setOf("false", "off", "0")
    private val legacyTrimRegex = Regex("""(?:\r?\n)*\{\{trim\}\}(?:\r?\n)*""", RegexOption.IGNORE_CASE)
    private val shorthandOpRegex = Regex(
        """^([.\$])([A-Za-z0-9_]+(?:-[A-Za-z0-9_]+)*)(?:\s*(\|\|=|\?\?=|\|\||\?\?|==|!=|>=|<=|\+=|-=|\+\+|--|[=<>])\s*(.*))?$""",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun substitute(text: String, env: MacroEnv): String {
        // 对齐官方 MacroEnvBuilder：contentHash = getStringHash(整个被替换文本)，全文档一致
        // 官方 core:legacy-markers：<USER>/<BOT>/<CHAR>/<GROUP>/<CHARIFNOTGROUP> → 宏
        val preprocessed = text
            .replace(Regex("""<USER>""", RegexOption.IGNORE_CASE), "{{user}}")
            .replace(Regex("""<BOT>""", RegexOption.IGNORE_CASE), "{{char}}")
            .replace(Regex("""<CHAR>""", RegexOption.IGNORE_CASE), "{{char}}")
            .replace(Regex("""<GROUP>""", RegexOption.IGNORE_CASE), "{{group}}")
            .replace(Regex("""<CHARIFNOTGROUP>""", RegexOption.IGNORE_CASE), "{{charIfNotGroup}}")
        val result = substituteWithEnv(preprocessed, env.copy(contentHash = StringHash.get(text)))
        // 官方 core:legacy-trim：{{trim}} 及其前后换行在全部宏处理完后移除
        return result.replace(legacyTrimRegex, "")
    }

    private fun substituteWithEnv(text: String, env: MacroEnv): String {
        val withScoped = replaceScopedMacros(text, env)
        val withoutScopedTrim = replaceScopedTrim(withScoped, env)
        // 作用域 if 在 replaceInline 内按文档顺序处理（setvar 等先执行）
        return replaceInline(withoutScopedTrim, env)
    }

    // ---------- 通用作用域宏（对齐官方 MacroCstWalker.processScopedMacros） ----------
    // {{setvar::name}}content{{/setvar}} → {{setvar::name::content}}
    // content 默认先求值嵌套宏再 trim+去缩进；{{#setvar::name}} 保留空白
    private val scopedMacroNames = setOf("setvar", "setglobalvar", "addvar", "addglobalvar", "incvar", "decvar")
    // inner 是 {{ 与 }} 之间的内容，正则应从 inner 开头匹配
    private val scopedOpenRegex = Regex("""^([!?~>#/]*)\s*([A-Za-z0-9_-]+)""")
    private val scopedCloseRegex = Regex("""^/\s*([A-Za-z0-9_-]+)\s*$""")

    private fun replaceScopedMacros(text: String, env: MacroEnv): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val open = text.indexOf("{{", i)
            if (open < 0) { sb.append(text, i, text.length); break }
            sb.append(text, i, open)
            val macroClose = findMacroClose(text, open + 2)
            if (macroClose < 0) { sb.append(text, open, text.length); break }
            val inner = text.substring(open + 2, macroClose)
            val m = scopedOpenRegex.find(inner) ?: run {
                sb.append(text, open, macroClose + 2)
                i = macroClose + 2
                continue
            }
            val flags = m.groupValues[1]
            val name = m.groupValues[2].lowercase()
            val preserveWhitespace = '#' in flags
            // 只处理白名单变量宏；if/trim 有专用路径；closing 宏跳过
            if (name !in scopedMacroNames || flags.contains('/')) {
                sb.append(text, open, macroClose + 2)
                i = macroClose + 2
                continue
            }
            val close = findMatchingScopedClose(text, macroClose + 2, name)
            if (close == null) {
                sb.append(text, open, macroClose + 2)
                i = macroClose + 2
                continue
            }
            val body = text.substring(macroClose + 2, close.first)
            val value = if (preserveWhitespace) {
                // 官方 #：仍求值嵌套宏，但跳过 trim
                substituteWithEnv(body, env)
            } else {
                trimScopedContent(substituteWithEnv(body, env))
            }
            // 对齐官方：content 作为最后一个 unnamed 参数追加
            sb.append("{{").append(inner.trim()).append("::").append(value).append("}}")
            // close.second 是 closing 第一个 } 的索引，跳过两个 } 
            i = close.second + 2
        }
        return sb.toString()
    }

    private fun findMatchingScopedClose(text: String, start: Int, name: String): Pair<Int, Int>? {
        var depth = 1
        var pos = start
        while (pos < text.length) {
            val open = text.indexOf("{{", pos)
            if (open < 0) return null
            val macroClose = findMacroClose(text, open + 2) ?: return null
            val inner = text.substring(open + 2, macroClose)
            val closeM = scopedCloseRegex.find(inner)
            if (closeM != null) {
                if (closeM.groupValues[1].lowercase() != name) {
                    pos = macroClose + 2
                    continue
                }
                depth--
                if (depth == 0) {
                    // closing 全局范围：open..macroClose
                    return open to macroClose
                }
                pos = macroClose + 2
                continue
            }
            val openM = scopedOpenRegex.find(inner)
            if (openM != null && openM.groupValues[2].lowercase() == name && '/' !in openM.groupValues[1]) {
                depth++
            }
            pos = macroClose + 2
        }
        return null
    }

    /** 对齐官方 MacroEngine.trimScopedContent：trim + 一致缩进去缩进。 */
    internal fun trimScopedContent(content: String, trimIndent: Boolean = true): String {
        if (content.isEmpty()) return ""
        // 对齐官方 trimIndent=false：只做基础 trim
        if (!trimIndent) return content.trim()
        val lines = content.split("\n".toRegex())
        var baseIndent = 0
        for (line in lines) {
            if (line.trim().isNotEmpty()) {
                baseIndent = line.takeWhile { it == ' ' || it == '\t' }.length
                break
            }
        }
        if (baseIndent == 0) return content.trim()
        val dedented = lines.joinToString("\n") { line ->
            val lineIndent = line.takeWhile { it == ' ' || it == '\t' }.length
            if (lineIndent >= baseIndent) line.drop(baseIndent) else line.trimStart()
        }
        return dedented.trim()
    }

    // ---------- 作用域 {{trim}}...{{/trim}} ----------
    private fun replaceScopedTrim(text: String, env: MacroEnv): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val open = Regex("""\{\{trim\}\}""").find(text, i) ?: break
            sb.append(text, i, open.range.first)
            val close = findMatchingTagClose(text, open.range.last + 1, "trim")
            if (close == null) {
                sb.append(open.value)
                i = open.range.last + 1
                continue
            }
            val body = text.substring(open.range.last + 1, close.range.first)
            sb.append(substituteWithEnv(replaceScopedTrim(body, env), env).trim())
            i = close.range.last + 1
        }
        if (i < text.length) sb.append(text, i, text.length)
        return sb.toString()
    }

    private fun findMatchingTagClose(text: String, start: Int, tag: String): MatchResult? {
        val openRe = Regex("""\{\{$tag\}\}""")
        val closeRe = Regex("""\{\{/$tag\}\}""")
        var depth = 1
        var pos = start
        while (pos < text.length) {
            val o = openRe.find(text, pos)
            val c = closeRe.find(text, pos)
            val next = when {
                o == null && c == null -> null
                o == null -> c
                c == null -> o
                else -> if (o.range.first < c.range.first) o else c
            } ?: break
            val isOpen = o != null && next.range.first == o.range.first
            if (isOpen) depth++ else depth--
            pos = next.range.last + 1
            if (depth == 0) return next
        }
        return null
    }

    private fun findMatchingClose(text: String, start: Int): MatchResult? {
        var depth = 1
        var pos = start
        while (pos < text.length) {
            val o = openingIfScan.find(text, pos)
            val c = closingIfRegex.find(text, pos)
            val next = when {
                o == null && c == null -> null
                o == null -> c
                c == null -> o
                else -> if (o.range.first < c.range.first) o else c
            } ?: break
            val isOpen = o != null && next.range.first == o.range.first
            if (isOpen) depth++ else depth--
            pos = next.range.last + 1
            if (depth == 0) return next
        }
        return null
    }

    private fun splitTopLevelElse(inner: String): Pair<String, String?> {
        var depth = 0
        var pos = 0
        while (pos < inner.length) {
            val o = openingIfScan.find(inner, pos)
            val c = closingIfRegex.find(inner, pos)
            val e = elseRegex.find(inner, pos)
            val next = listOfNotNull(o, c, e).minByOrNull { it.range.first } ?: break
            val isOpen = o != null && next.range.first == o.range.first
            val isClose = c != null && next.range.first == c.range.first
            when {
                isOpen -> depth++
                isClose -> depth--
                depth == 0 -> return inner.substring(0, next.range.first) to
                    inner.substring(next.range.last + 1)
            }
            pos = next.range.last + 1
        }
        return inner to null
    }

    /** 返回 isFalsy（已含取反处理）。 */
    private fun evaluateCondition(rawCondition: String, env: MacroEnv): Boolean {
        val inverted = rawCondition.startsWith("!")
        val condition = if (inverted) rawCondition.substring(1).trim() else rawCondition.trim()
        var resolved = replaceInline(condition, env)
        // 变量简写（.var / $var）：对齐官方 getvar / getglobalvar
        val shorthand = Regex("""^([.\$])([A-Za-z0-9_]+)$""").matchEntire(resolved)
        if (shorthand != null) {
            val store = if (shorthand.groupValues[1] == ".") env.local else env.global
            resolved = store.get(shorthand.groupValues[2]) ?: ""
        }
        // 条件为单个宏名：已注册宏解析（noop→空、char→名字）；未注册保持字面量
        if (Regex("""^[a-zA-Z_][a-zA-Z0-9_]*$""").matches(resolved)) {
            val attempted = replaceInline("{{$resolved}}", env)
            if (attempted != "{{$resolved}}") resolved = attempted
        }
        var falsy = resolved.isEmpty() || isFalseBoolean(resolved)
        if (inverted) falsy = !falsy
        return falsy
    }

    private fun isFalseBoolean(value: String): Boolean =
        value.trim().lowercase() in falsyValues

    // ---------- 行内宏 ----------
    private fun replaceInline(text: String, env: MacroEnv): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val open = text.indexOf("{{", i)
            if (open < 0) { sb.append(text, i, text.length); break }
            sb.append(text, i, open)
            val macroClose = findMacroClose(text, open + 2)
            if (macroClose < 0) {
                // 未闭合：按单个 '{' 推进，后续合法宏仍可解析（官方括号边界行为）
                sb.append('{')
                i = open + 1
                continue
            }
            val inner = text.substring(open + 2, macroClose)
            val raw = text.substring(open, macroClose + 2)
            // 对齐官方 MacroFlags：剥离 !?~#> 前缀（/ 是 closing 不剥离），如 {{#setvar::x::v}} → setvar
            val flagStripped = inner.trimStart().dropWhile { it in "!?~#>" }.trimStart()

            if (inner.startsWith("//")) {
                i = macroClose + 2
                continue
            }
            val shortInner = flagStripped
            if (shortInner.startsWith(".") || shortInner.startsWith("$")) {
                val m = shorthandOpRegex.matchEntire(shortInner)
                if (m == null) {
                    sb.append(raw)
                    i = macroClose + 2
                    continue
                }
                val store = if (m.groupValues[1] == ".") env.local else env.global
                val name = m.groupValues[2]
                val op = m.groupValues[3]
                val rawValue = m.groupValues[4].trim()
                val value = if (rawValue.contains("{{")) substituteWithEnv(rawValue, env) else rawValue
                when (op) {
                    "" -> sb.append(store.get(name) ?: "")
                    "=" -> store.set(name, value)
                    "+=" -> addVariable(store, name, value)
                    "-=" -> addVariable(store, name, "-$value")
                    "++" -> sb.append(addVariable(store, name, "1"))
                    "--" -> sb.append(addVariable(store, name, "-1"))
                    "||" -> {
                        val current = store.get(name)
                        if (current == null || current.isEmpty() || isFalseBoolean(current)) {
                            sb.append(value)
                        } else {
                            sb.append(current)
                        }
                    }
                    "??" -> sb.append(store.get(name) ?: value)
                    "||=" -> {
                        val current = store.get(name)
                        if (current == null || current.isEmpty() || isFalseBoolean(current)) {
                            store.set(name, value)
                            sb.append(value)
                        } else {
                            sb.append(current)
                        }
                    }
                    "??=" -> {
                        val current = store.get(name)
                        if (current == null) {
                            store.set(name, value)
                            sb.append(value)
                        } else {
                            sb.append(current)
                        }
                    }
                    "==" -> sb.append(if ((store.get(name) ?: "") == value) "true" else "false")
                    "!=" -> sb.append(if ((store.get(name) ?: "") != value) "true" else "false")
                    ">", ">=", "<", "<=" -> {
                        val cmp = compareNumeric(store.get(name), value)
                        val result = cmp != null && when (op) {
                            ">" -> cmp > 0
                            ">=" -> cmp >= 0
                            "<" -> cmp < 0
                            else -> cmp <= 0
                        }
                        sb.append(if (result) "true" else "false")
                    }
                }
                i = macroClose + 2
                continue
            }
            if (flagStripped.startsWith("/") || flagStripped.startsWith("else")) {
                sb.append(raw)
                i = macroClose + 2
                continue
            }

            val (name, args, hasSep) = parseMacroHead(flagStripped)
            if (name.isEmpty()) {
                sb.append('{')
                i = open + 1
                continue
            }

            // 作用域 {{if 条件}}...{{/if}}：按文档顺序求值（嵌套宏条件已完整捕获）
            if (
                name.equals("if", ignoreCase = true) && hasSep &&
                ((flagStripped.length > 2 && flagStripped[2].isWhitespace()) || flagStripped.startsWith("if::"))
            ) {
                val ifClose = findMatchingClose(text, macroClose + 2)
                if (ifClose != null) {
                    val conditionRaw = args
                    val innerText = text.substring(macroClose + 2, ifClose.range.first)
                    val evaluated = evaluateCondition(conditionRaw, env)
                    val split = splitTopLevelElse(innerText)
                    val chosen = if (!evaluated) split.first else split.second
                    sb.append(if (chosen != null) substituteWithEnv(chosen, env).trim() else "")
                    i = ifClose.range.last + 1
                    continue
                }
            }

            // 官方：嵌套宏先内层后外层（inside-out）
            val resolvedArgs = if (hasSep && args.contains("{{")) substituteWithEnv(args, env) else args
            sb.append(resolve(name, resolvedArgs, env, open, raw))
            i = macroClose + 2
        }
        // 注释宏 {{// ...}} -> ''
        val out = sb.toString().replace(Regex("""\{\{//[^{}]*\}\}"""), "")
        // 孤立标记清理
        return out.replace(Regex("""\{\{(?:else|/if)(?:::[^{}]*)?\}\}"""), "")
    }

    /** 找到与 {{ 配对的 }}，支持嵌套 {{...}}。 */
    private fun findMacroClose(text: String, from: Int): Int {
        var depth = 1
        var i = from
        while (i < text.length) {
            if (text.startsWith("{{", i)) { depth++; i += 2; continue }
            if (text.startsWith("}}", i)) {
                depth--
                if (depth == 0) return i
                i += 2
                continue
            }
            i++
        }
        return -1
    }

    /** 解析 {{name...}} 内部：名字 + 分隔符（:: / : / 空格）+ 参数。 */
    private fun parseMacroHead(inner: String): Triple<String, String, Boolean> {
        var j = 0
        while (j < inner.length && (inner[j].isLetterOrDigit() || inner[j] == '_')) j++
        val name = inner.substring(0, j)
        if (j >= inner.length) return Triple(name, "", false)
        return when {
            inner.startsWith("::", j) -> Triple(name, inner.substring(j + 2), true)
            inner[j] == ':' -> Triple(name, inner.substring(j + 1), true)
            inner[j].isWhitespace() -> Triple(name, inner.substring(j + 1).trim(), true)
            else -> Triple(name, "", false)
        }
    }

    /** 数值比较：两侧都可解析为数字时比较，否则 null（恒假）。 */
    private fun compareNumeric(left: String?, right: String): Int? {
        val l = left?.trim()?.toDoubleOrNull()
        val r = right.trim().toDoubleOrNull()
        return if (l != null && r != null) l.compareTo(r) else null
    }

    private fun resolve(name: String, args: String, env: MacroEnv, offset: Int, raw: String): String =
        when (name.lowercase()) {
            "user" -> env.user
            "char" -> env.char
            "group", "charifnotgroup" -> env.group.ifEmpty { env.char }
            "groupnotmuted" -> env.groupNotMuted
            "notchar" -> env.notChar
            "charprompt" -> env.character.charPrompt
            "charinstruction" -> env.character.charInstruction
            "chardescription", "description" -> env.character.description
            "charpersonality", "personality" -> env.character.personality
            "charscenario", "scenario" -> env.character.scenario
            "persona" -> env.character.persona
            "mesexamplesraw" -> env.character.mesExamplesRaw
            "mesexamples" -> formatMesExamples(env.character.mesExamplesRaw)
            "chardepthprompt" -> env.character.charDepthPrompt
            "charcreatornotes", "creatornotes" -> env.character.creatorNotes
            "charfirstmessage", "greeting" -> greetingMacro(args, env.character)
            "charversion", "version", "char_version" -> env.character.version
            "model" -> env.system.model
            "summary" -> env.summary
            "ismobile" -> (if (env.isMobile) "true" else "false")
            "space" -> " "
            "newline" -> "\n"
            "noop" -> ""
            // {{trim::text}} 工具宏；{{trim}} 无参留给 legacy-trim 后处理
            "trim" -> if (args.isEmpty()) raw else args.trim()
            "reverse" -> args.reversed()
            "//", "comment" -> ""
            // instruct 模板宏（对齐 macros/definitions/instruct-macros.js）
            "instructstorystringprefix" -> instructValue(env) { it.storyStringPrefix }
            "instructstorystringsuffix" -> instructValue(env) { it.storyStringSuffix }
            "instructuserprefix", "instructinput" -> instructValue(env) { it.inputSequence }
            "instructusersuffix" -> instructValue(env) { it.inputSuffix }
            "instructassistantprefix", "instructoutput" -> instructValue(env) { it.outputSequence }
            "instructassistantsuffix", "instructseparator" -> instructValue(env) { it.outputSuffix }
            "instructsystemprefix" -> instructValue(env) { it.systemSequence }
            "instructsystemsuffix" -> instructValue(env) { it.systemSuffix }
            "instructfirstassistantprefix", "instructfirstoutputprefix" ->
                instructValue(env) { it.firstOutputSequence.ifEmpty { it.outputSequence } }
            "instructlastassistantprefix", "instructlastoutputprefix" ->
                instructValue(env) { it.lastOutputSequence.ifEmpty { it.outputSequence } }
            "instructstop" -> instructValue(env) { it.stopSequence }
            "instructuserfiller" -> instructValue(env) { it.userAlignmentMessage }
            "instructsysteminstructionprefix" -> instructValue(env) { it.lastSystemSequence }
            "instructfirstuserprefix", "instructfirstinput" ->
                instructValue(env) { it.firstInputSequence.ifEmpty { it.inputSequence } }
            "instructlastuserprefix", "instructlastinput" ->
                instructValue(env) { it.lastInputSequence.ifEmpty { it.inputSequence } }
            "original" -> env.original
            "var" -> env.slash?.variable(args.trim()) ?: ""
            "pipe" -> env.slash?.pipe() ?: ""
            "arg" -> env.slash?.argument(args.trim()) ?: ""
            "systemprompt" -> if (!env.systemPromptEnabled) {
                ""
            } else if (env.preferCharacterPrompt && env.character.charPrompt.isNotEmpty()) {
                env.character.charPrompt
            } else {
                env.systemPromptContent
            }
            "defaultsystemprompt", "instructsystem", "instructsystemprompt" ->
                if (env.systemPromptEnabled) env.systemPromptContent else ""
            "exampleseparator", "chatseparator" -> env.context?.exampleSeparator ?: ""
            "chatstart" -> env.context?.chatStart ?: ""
            "input" -> env.input
            "maxprompt", "maxprompttokens" -> env.maxPromptTokens.toString()
            "maxcontext", "maxcontexttokens" -> env.maxContextTokens.toString()
            "maxresponse", "maxresponsetokens" -> env.maxResponseTokens.toString()
            "lastgenerationtype" -> env.lastGenerationType
            "hasextension" -> (if (env.extensions.contains(args.trim())) "true" else "false")
            "outlet" -> if (args.isBlank()) "" else env.outlets[args.trim()] ?: "" 
            "lastmessage" -> lastMessageMacro(env)
            "lastmessageid" -> lastMessageIdMacro(env)?.toString() ?: ""
            "lastusermessage" -> lastFilteredMessage(env, true)
            "lastcharmessage" -> lastFilteredMessage(env, false)
            "allchatrange" -> allChatRangeMacro(env)
            "firstincludedmessageid" -> (env.firstIncludedMessageId?.toString() ?: "")
            "firstdisplayedmessageid" -> (env.firstDisplayedMessageId?.toString() ?: "")
            "lastswipeid" -> lastSwipeIdMacro(env)
            "currentswipeid" -> currentSwipeIdMacro(env)
            "isotime" -> LocalTime.now().format(hhMm)
            "isodate" -> LocalDate.now().format(yyyyMmDd)
            "time" -> timeMacro(args)
            "date" -> LocalDate.now().format(monthDayYear)
            "weekday" -> LocalDate.now().dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
            "random" -> randomMacro(args)
            "roll" -> rollMacro(args)
            "pick" -> pickMacro(args, env, offset, raw)
            "if" -> inlineIfMacro(args, env)
            "getvar" -> env.slash?.variable(args.trim()) ?: env.local.get(args.trim()) ?: ""
            "hasvar" -> (if (env.local.has(args.trim())) "true" else "false")
            "deletevar" -> { env.local.delete(args.trim()); "" }
            "setvar" -> { setVariableArgs(args, env.local); "" }
            "addvar" -> { addVariableArgs(args, env.local); "" }
            "incvar" -> incDecVariableArgs(args, env.local, 1)
            "decvar" -> incDecVariableArgs(args, env.local, -1)
            "getglobalvar" -> env.global.get(args.trim()) ?: ""
            "hasglobalvar" -> (if (env.global.has(args.trim())) "true" else "false")
            "deleteglobalvar" -> { env.global.delete(args.trim()); "" }
            "setglobalvar" -> { setVariableArgs(args, env.global); "" }
            "addglobalvar" -> { addVariableArgs(args, env.global); "" }
            "incglobalvar" -> incDecVariableArgs(args, env.global, 1)
            "decglobalvar" -> incDecVariableArgs(args, env.global, -1)
            // 自定义宏优先；官方：未知宏保留语法，但嵌套参数已解析
            else -> MacroRegistry.resolve(name.lowercase(), args, env)
                ?: if (args.isEmpty()) raw else "{{" + name + "::" + args + "}}"
        }

    private fun lastMessageIdMacro(env: MacroEnv): Int? {
        for (i in env.chat.indices.reversed()) {
            val m = env.chat[i]
            if (m.swipes.isNotEmpty() && m.swipeId >= m.swipes.size) continue
            return i
        }
        return null
    }

    private fun lastMessageMacro(env: MacroEnv): String {
        val id = lastMessageIdMacro(env) ?: return ""
        return env.chat[id].mes
    }

    private fun lastFilteredMessage(env: MacroEnv, wantUser: Boolean): String {
        for (i in env.chat.indices.reversed()) {
            val m = env.chat[i]
            if (m.isSystem) continue
            if (m.isUser == wantUser) return m.mes
        }
        return ""
    }

    private fun allChatRangeMacro(env: MacroEnv): String =
        if (env.chat.isEmpty()) "" else "0-${env.chat.size - 1}"

    private fun lastSwipeIdMacro(env: MacroEnv): String {
        val id = lastMessageIdMacro(env) ?: return ""
        val swipes = env.chat[id].swipes
        return if (swipes.isEmpty()) "" else swipes.size.toString()
    }

    private fun currentSwipeIdMacro(env: MacroEnv): String {
        val id = lastMessageIdMacro(env) ?: return ""
        return (env.chat[id].swipeId + 1).toString()
    }

    /** 对齐 parseMesExamples：按 <START> 切分、trim、去空；非 instruct 直接拼接。 */
    private fun formatMesExamples(raw: String): String {
        if (raw.isEmpty()) return ""
        val parsed = raw.split(Regex("""<START>""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parsed.isEmpty()) return ""
        return parsed.joinToString("")
    }

    /** 对齐 charFirstMessage：0=主开场白，1+ 取备选开场白。 */
    private fun greetingMacro(args: String, character: CharacterFields): String {
        val index = args.trim().toIntOrNull() ?: 0
        if (index == 0) return character.firstMessage
        return character.alternateGreetings.getOrNull(index - 1) ?: ""
    }

    private fun setVariableArgs(args: String, store: VariableStore) {
        val sep = args.indexOf("::")
        if (sep < 0) return
        store.set(args.substring(0, sep).trim(), args.substring(sep + 2))
    }

    private fun addVariableArgs(args: String, store: VariableStore) {
        val sep = args.indexOf("::")
        if (sep < 0) return
        addVariable(store, args.substring(0, sep).trim(), args.substring(sep + 2))
    }

    private fun incDecVariableArgs(args: String, store: VariableStore, delta: Int): String {
        val name = args.trim()
        if (name.isEmpty()) return ""
        return addVariable(store, name, delta.toString())
    }

    private fun inlineIfMacro(args: String, env: MacroEnv): String {
        // 内联形式 {{if 条件::内容}}：2 个参数
        val sep = args.indexOf("::")
        if (sep < 0) return ""
        val condition = args.substring(0, sep).trim()
        val content = args.substring(sep + 2)
        val falsy = evaluateCondition(condition, env)
        return if (!falsy) substituteWithEnv(content, env).trim() else ""
    }

    /** 官方 instruct 宏：仅当 instruct 启用时返回设置值。 */
    private fun instructValue(env: MacroEnv, selector: (InstructSettings) -> String): String {
        val settings = env.instruct
        return if (settings?.enabled == true) selector(settings) else ""
    }

    private val hhMm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val yyyyMmDd: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val monthDayYear: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
    private val shortTime: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.US)

    private fun shortLocalTime(): String =
        LocalTime.now().format(shortTime)

    private fun timeMacro(args: String): String {
        if (args.isBlank()) return shortLocalTime()
        val m = Regex("""^UTC([+-]\d+)$""").matchEntire(args.trim())
        if (m == null) return shortLocalTime()
        val offset = m.groupValues[1].toIntOrNull() ?: return shortLocalTime()
        return LocalTime.now(ZoneOffset.ofHours(offset))
            .format(shortTime)
    }

    private fun splitList(args: String): List<String> {
        val raw = args.trim()
        if (raw.isEmpty()) return emptyList()
        return if ("::" in raw) raw.split("::").map { it.trim() }.filter { it.isNotEmpty() }
        else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun randomMacro(args: String): String {
        val list = splitList(args)
        if (list.isEmpty()) return ""
        return list[Random.nextInt(list.size)]
    }

    private fun rollMacro(args: String): String {
        val formula = args.trim()
        val m = Regex("""^(\d*)d(\d+)([+-]\d+)?$""", RegexOption.IGNORE_CASE).matchEntire(formula)
        if (m != null) {
            val count = m.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
            val sides = m.groupValues[2].toIntOrNull() ?: return ""
            val modifier = m.groupValues[3].toIntOrNull() ?: 0
            var total = modifier
            repeat(count) { total += Random.nextInt(1, sides + 1) }
            return total.toString()
        }
        return formula.toIntOrNull()?.toString() ?: ""
    }

    private fun pickMacro(args: String, env: MacroEnv, offset: Int, raw: String): String {
        val list = splitList(args)
        if (list.isEmpty()) return ""
        // 官方：combinedSeedString = [chatIdHash, contentHash, offset, rerollSeed].filter(!null).join('-')
        val seedParts = buildList {
            add(env.chatIdHash.toString())
            add(env.contentHash.toString())
            add(offset.toString())
            env.rerollSeed?.let { add(it.toString()) }
        }
        val finalSeed = StringHash.get(seedParts.joinToString("-"))
        // 官方：seedrandom(String(finalSeed))；randomIndex = Math.floor(rng() * list.length)
        val rng = SeedRandom(finalSeed.toString())
        val randomIndex = Math.floor(rng.nextDouble() * list.size).toInt()
        return list[randomIndex]
    }
}
