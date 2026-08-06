package com.emberinn.engine.macros

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
    val firstIncludedMessageId: Int? = null,
    val firstDisplayedMessageId: Int? = null,
    val extensions: Set<String> = emptySet(),
    val isMobile: Boolean = false,
    val chatId: Long = 0,
    val chatIdHash: Long = 0,
    val rerollSeed: Long? = null,
    val contentHash: Long = 0,
    val local: VariableStore = EmptyVariableStore,
    val global: VariableStore = EmptyVariableStore,
)

/**
 * 核心宏引擎（Stage 2）：
 * {{user}} {{char}} {{time}} {{date}} {{weekday}} {{isotime}} {{isodate}}
 * {{random::a::b}} {{roll::NdM}} {{pick::a::b}}
 * {{if 条件}}then{{else}}other{{/if}}（嵌套/取反/falsy/顶层 else/只解析选中分支，对齐官方 core-macros.js if 宏）
 * 未实现的宏原样保留；变量宏（getvar 等）与 seedrandom 逐位一致属 Stage 3。
 */
object MacroEngine {

    private val macroRegex = Regex("""\{\{([a-zA-Z_]+)(?:::(.*?))?\}\}""")
    private val spaceArgsRegex = Regex("""\{\{([a-zA-Z_]+)\s+([^{}]*?)\}\}""")
    private val scopedIfRegex = Regex("""\{\{if\s+([\s\S]*?)\}\}""")
    private val closingIfRegex = Regex("""\{\{/if\}\}""")
    private val openingIfScan = Regex("""\{\{if\s""")
    private val elseRegex = Regex("""\{\{else(?:::[^{}]*)?\}\}""")

    private val zeroArgMacros = setOf("user", "char", "isotime", "isodate", "time", "date", "weekday")
    private val variableShorthandRegex = Regex("""\{\{([.\$])([A-Za-z0-9_]+)\}\}""")
    private val spaceArgMacros = setOf("roll", "random", "pick", "time")
    private val falsyValues = setOf("false", "off", "0")

    fun substitute(text: String, env: MacroEnv): String {
        // 对齐官方 MacroEnvBuilder：contentHash = getStringHash(整个被替换文本)，全文档一致
        return substituteWithEnv(text, env.copy(contentHash = StringHash.get(text)))
    }

    private fun substituteWithEnv(text: String, env: MacroEnv): String {
        val withoutScoped = replaceScopedIf(text, env)
        return replaceInline(withoutScoped, env)
    }

    // ---------- 作用域 if 块 ----------
    private fun replaceScopedIf(text: String, env: MacroEnv): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val open = scopedIfRegex.find(text, i) ?: break
            sb.append(text, i, open.range.first)
            val bodyStart = open.range.last + 1
            val close = findMatchingClose(text, bodyStart)
            if (close == null) {
                sb.append(open.value)
                i = open.range.last + 1
                continue
            }
            val inner = text.substring(bodyStart, close.range.first)
            val condition = open.groupValues[1].trim()
            val evaluated = evaluateCondition(condition, env)
            val split = splitTopLevelElse(inner)
            val chosen = if (!evaluated) split.first else split.second
            val result = if (chosen != null) substituteWithEnv(chosen, env).trim() else ""
            sb.append(result)
            i = close.range.last + 1
        }
        if (i < text.length) sb.append(text, i, text.length)
        return sb.toString()
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
        // 单参数宏名（minArgs=0）自动解析
        if (Regex("""^[a-zA-Z_][a-zA-Z0-9_]*$""").matches(resolved) && resolved.lowercase() in zeroArgMacros) {
            resolved = replaceInline("{{$resolved}}", env)
        }
        var falsy = resolved.isEmpty() || isFalseBoolean(resolved)
        if (inverted) falsy = !falsy
        return falsy
    }

    private fun isFalseBoolean(value: String): Boolean =
        value.trim().lowercase() in falsyValues

    // ---------- 行内宏 ----------
    private fun replaceInline(text: String, env: MacroEnv): String {
        var out = macroRegex.replace(text) { m ->
            val name = m.groupValues[1]
            val args = m.groupValues[2]
            val raw = m.value
            resolve(name, args, env, m.range.first, raw)
        }
        // 旧式空格参数：{{roll 1d20}} {{random a,b}} {{pick a,b}} {{time UTC+2}}
        out = spaceArgsRegex.replace(out) { m ->
            val name = m.groupValues[1].lowercase()
            if (name in spaceArgMacros && !m.value.contains("::")) {
                resolve(name, m.groupValues[2], env, m.range.first, m.value)
            } else {
                m.value
            }
        }
        // 变量简写 {{.name}} / {{$name}}（无运算符版）
        out = variableShorthandRegex.replace(out) { m ->
            val prefix = m.groupValues[1]
            val name = m.groupValues[2]
            val store = if (prefix == ".") env.local else env.global
            store.get(name) ?: ""
        }
        // 注释宏 {{// ...}} -> ''
        out = out.replace(Regex("""\{\{//[^{}]*\}\}"""), "")
        // 孤立标记清理
        return out.replace(Regex("""\{\{(?:else|/if)(?:::[^{}]*)?\}\}"""), "")
    }

    private fun resolve(name: String, args: String, env: MacroEnv, offset: Int, raw: String): String =
        when (name.lowercase()) {
            "user" -> env.user
            "char" -> env.char
            "group", "charifnotgroup" -> env.group
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
            "ismobile" -> (if (env.isMobile) "true" else "false")
            "space" -> " "
            "newline" -> "\n"
            "noop" -> ""
            "trim" -> args.trim()
            "reverse" -> args.reversed()
            "//", "comment" -> ""
            "input" -> env.input
            "maxprompt", "maxprompttokens" -> env.maxPromptTokens.toString()
            "maxcontext", "maxcontexttokens" -> env.maxContextTokens.toString()
            "maxresponse", "maxresponsetokens" -> env.maxResponseTokens.toString()
            "lastgenerationtype" -> env.lastGenerationType
            "hasextension" -> (if (env.extensions.contains(args.trim())) "true" else "false")
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
            "getvar" -> env.local.get(args.trim()) ?: ""
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
            else -> raw
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

    private val hhMm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val yyyyMmDd: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun shortLocalTime(): String =
        LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))

    private fun timeMacro(args: String): String {
        if (args.isBlank()) return shortLocalTime()
        val m = Regex("""^UTC([+-]\d+)$""").matchEntire(args.trim())
        if (m == null) return shortLocalTime()
        val offset = m.groupValues[1].toIntOrNull() ?: return shortLocalTime()
        return LocalTime.now(ZoneOffset.ofHours(offset))
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))
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
