package com.emberinn.engine.macros

import com.emberinn.engine.worldinfo.StringHash
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.random.Random

/** 宏环境：对齐 MacroEnv 的核心字段。 */
data class MacroEnv(
    val user: String,
    val char: String,
    val chatId: Long = 0,
    val rerollSeed: Long? = null,
)

/**
 * 核心宏引擎（Stage 1）：{{user}} {{char}} {{time}} {{date}} {{weekday}} {{isotime}} {{isodate}}
 * {{random::a::b}} {{roll::NdM}} {{pick::a::b}}，语义对齐官方 macros/definitions。
 * 注意：if/变量宏、seedrandom 逐位一致、chatIdHash/contentHash 精确取值属于 Stage 2（配合提示词组装）。
 * 未实现的宏原样保留，不吞内容。
 */
object MacroEngine {

    private val macroRegex = Regex("""\{\{([a-zA-Z_]+)(?:::(.*?))?\}\}""")
    private val hhMm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val yyyyMmDd: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun substitute(text: String, env: MacroEnv): String {
        var offset = 0
        return macroRegex.replace(text) { m ->
            val name = m.groupValues[1]
            val args = m.groupValues[2]
            val result = resolve(name, args, env, offset, m.value)
            offset += 1
            result
        }
    }

    private fun resolve(name: String, args: String, env: MacroEnv, offset: Int, raw: String): String =
        when (name.lowercase()) {
            "user" -> env.user
            "char" -> env.char
            "isotime" -> LocalTime.now().format(hhMm)
            "isodate" -> LocalDate.now().format(yyyyMmDd)
            "time" -> timeMacro(args)
            "date" -> LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault()))
            "weekday" -> LocalDate.now().dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
            "random" -> randomMacro(args)
            "roll" -> rollMacro(args)
            "pick" -> pickMacro(args, env, offset, raw)
            else -> raw
        }

    private fun shortLocalTime(): String =
        LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))

    private fun timeMacro(args: String): String {
        if (args.isBlank()) return shortLocalTime()
        val m = Regex("^UTC([+-]\\d+)$").matchEntire(args.trim())
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
        val m = Regex("^(\\d*)d(\\d+)([+-]\\d+)?$", RegexOption.IGNORE_CASE).matchEntire(formula)
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
        val contentHash = StringHash.get(raw)
        val seedParts = buildList {
            add(env.chatId.toString())
            add(contentHash.toString())
            add(offset.toString())
            env.rerollSeed?.let { add(it.toString()) }
        }
        val seed = StringHash.get(seedParts.joinToString("-"))
        val index = (seed % list.size).toInt()
        return list[index]
    }
}
