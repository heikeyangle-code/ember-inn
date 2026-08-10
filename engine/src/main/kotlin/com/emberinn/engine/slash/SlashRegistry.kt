package com.emberinn.engine.slash

import com.emberinn.engine.worldinfo.TokenCounterFactory
import com.emberinn.engine.worldinfo.VectorTextUtils
import com.emberinn.engine.worldinfo.WorldRegexUtils
import kotlinx.serialization.json.jsonArray
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 命令解析器（App 层可注入带状态的命令；默认走全局注册表）。 */
fun interface SlashCommandResolver {
    fun resolve(name: String): SlashCommandDef?
}

/** 命令注册表 + 内置命令（对齐官方 registerSlashCommand 的核心）。 */
object SlashRegistry : SlashCommandResolver {

    private val commands = linkedMapOf<String, SlashCommandDef>()

    fun register(def: SlashCommandDef) {
        commands[def.name] = def
        def.aliases.forEach { commands[it] = def }
    }

    fun get(name: String): SlashCommandDef? = commands[name.lowercase()]

    override fun resolve(name: String): SlashCommandDef? = get(name)

    fun execute(line: String): String {
        val invocation = SlashParser.parse(line)
        val def = get(invocation.name) ?: throw SlashParseException("未知命令: /${invocation.name}")
        return def.callback(invocation, SlashState())
    }

    fun all(): List<SlashCommandDef> = commands.values.distinctBy { it.name }

    init {
        register(
            SlashCommandDef(
                name = "help",
                description = "列出可用命令",
                callback = { _, _ -> all().joinToString("\n") { "/${it.name} — ${it.description}" } },
            ),
        )
        register(SlashCommandDef("continue", description = "继续生成上一条消息", callback = { _, _ -> "OK:continue" }))
        register(SlashCommandDef("regenerate", description = "重新生成最后一条消息", callback = { _, _ -> "OK:regenerate" }))
        register(
            SlashCommandDef(
                "swipe",
                description = "切换回复（可带方向）",
                callback = { inv, _ -> "OK:swipe:${inv.namedArgs["direction"] ?: inv.unnamedArgs.firstOrNull() ?: "right"}" },
            ),
        )
        register(SlashCommandDef("sys", aliases = listOf("nar"), description = "以系统/旁白身份发送消息", rawQuotes = true, callback = { inv, _ -> "OK:sys:${inv.unnamedArgs.joinToString(" ")}" }))
        register(
            SlashCommandDef(
                "sendas",
                description = "以指定角色发送消息（name= 必填）",
                rawQuotes = true,
                callback = { inv, _ -> "OK:sendas:${inv.namedArgs["name"] ?: ""}:${inv.unnamedArgs.joinToString(" ")}" },
            ),
        )
        register(
            SlashCommandDef(
                "echo",
                description = "原样返回无名参数",
                callback = { inv, _ -> inv.unnamedArgs.joinToString(" ") },
                rawQuotes = true,
            ),
        )
        register(
            SlashCommandDef(
                "pass",
                aliases = listOf("return"),
                description = "把文本传给下一条命令（管道透传）",
                callback = { inv, _ -> inv.unnamedArgs.joinToString(" ") },
            ),
        )
        register(
            SlashCommandDef(
                "persona",
                aliases = listOf("persona-set"),
                description = "切换人设（mode=lookup/temp/all，默认 all：先找人设，找不到回退临时用户名）",
                callback = { inv, _ -> "OK:persona:${inv.unnamedArgs.joinToString(" ")}:mode=${inv.namedArgs["mode"] ?: "all"}" },
            ),
        )
        register(
            SlashCommandDef(
                "let",
                description = "设置作用域变量（对齐官方 /let）",
                splitUnnamedArgument = true,
                splitUnnamedArgumentCount = 1,
                callback = { inv, state ->
                    val key = inv.namedArgs["key"] ?: inv.unnamedArgs.firstOrNull() ?: return@SlashCommandDef ""
                    val value = if (inv.namedLists.containsKey("key")) {
                        // 官方：list 值存为 JSON 数组，供 {{var::key::index}}
                        "[" + inv.namedLists["key"]!!.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" } + "]"
                    } else if (inv.namedArgs.containsKey("key")) {
                        inv.unnamedArgs.joinToString(" ")
                    } else {
                        inv.unnamedArgs.drop(1).joinToString(" ")
                    }
                    state.variables[key] = value
                    ""
                },
            ),
        )
        register(
            SlashCommandDef(
                "qr-arg",
                description = "设置 {{arg}} 参数（对齐官方 /qr-arg）",
                splitUnnamedArgument = true,
                splitUnnamedArgumentCount = 2,
                callback = { inv, state ->
                    val name = inv.unnamedArgs.firstOrNull() ?: return@SlashCommandDef ""
                    state.arguments[name] = inv.unnamedArgs.drop(1).joinToString(" ")
                    ""
                },
            ),
        )
        register(
            SlashCommandDef(
                "setvar",
                description = "设置作用域变量（{{getvar}} 可读）",
                splitUnnamedArgument = true,
                splitUnnamedArgumentCount = 1,
                callback = { inv, state ->
                    val key = inv.namedArgs["key"] ?: inv.unnamedArgs.firstOrNull() ?: return@SlashCommandDef ""
                    val value = if (inv.namedArgs.containsKey("key")) {
                        inv.unnamedArgs.joinToString(" ")
                    } else {
                        inv.unnamedArgs.drop(1).joinToString(" ")
                    }
                    state.variables[key] = value
                    ""
                },
            ),
        )
        register(
            SlashCommandDef(
                "getvar",
                aliases = listOf("getchatvar"),
                description = "读取作用域变量并传给管道（支持 index）",
                callback = { inv, state ->
                    val key = inv.namedArgs["key"] ?: inv.unnamedArgs.firstOrNull() ?: return@SlashCommandDef ""
                    val index = inv.namedArgs["index"]
                    val lookup = if (index != null) "$key::$index" else key
                    state.variable(lookup) ?: ""
                },
            ),
        )
        register(
            SlashCommandDef(
                "addvar",
                aliases = listOf("addchatvar"),
                description = "给作用域变量增加值（数组则追加，数字相加，否则拼接）",
                callback = { inv, state ->
                    val key = inv.namedArgs["key"] ?: inv.namedArgs["name"] ?: return@SlashCommandDef ""
                    val value = inv.unnamedArgs.joinToString(" ")
                    val result = addVariable(state.variables[key], value)
                    state.variables[key] = result
                    result
                },
            ),
        )
        register(
            SlashCommandDef(
                "incvar",
                aliases = listOf("incchatvar"),
                description = "作用域变量 +1",
                callback = { inv, state ->
                    val key = inv.namedArgs["key"] ?: inv.namedArgs["name"] ?: inv.unnamedArgs.firstOrNull()
                        ?: return@SlashCommandDef ""
                    val current = state.variables[key]
                    val result = addVariable(current, "1")
                    state.variables[key] = result
                    result
                },
            ),
        )
        register(
            SlashCommandDef(
                "decvar",
                aliases = listOf("decchatvar"),
                description = "作用域变量 -1",
                callback = { inv, state ->
                    val key = inv.namedArgs["key"] ?: inv.namedArgs["name"] ?: inv.unnamedArgs.firstOrNull()
                        ?: return@SlashCommandDef ""
                    val current = state.variables[key]
                    val result = addVariable(current, "-1")
                    state.variables[key] = result
                    result
                },
            ),
        )
        register(
            SlashCommandDef(
                "parser-flag",
                description = "解析器标志：STRICT_ESCAPING / REPLACE_GETVAR（对齐官方 /parser-flag）",
                splitUnnamedArgument = true,
                callback = { inv, state ->
                    val flag = inv.unnamedArgs.firstOrNull() ?: return@SlashCommandDef ""
                    val on = inv.unnamedArgs.getOrNull(1)?.lowercase() in setOf("on", "true", "1", "yes", "y") ||
                        inv.unnamedArgs.getOrNull(1) == null
                    when (flag.uppercase()) {
                        "STRICT_ESCAPING" -> state.strictEscaping = on
                        "REPLACE_GETVAR" -> state.replaceGetvar = on
                    }
                    ""
                },
            ),
        )

        // ---- 会话/输入/背景/冒充/触发命令（官方 slash-commands.js；真正执行由 App SlashMessageActions 注入，引擎占位）----
        register(SlashCommandDef("trigger", description = "触发一次生成（Generate('normal')）", callback = { _, _ -> "OK:trigger" }))
        register(SlashCommandDef("inject", description = "注入一段提示文本（返回注入 ID）", rawQuotes = true, callback = { inv, _ -> "OK:inject:${inv.namedArgs["id"] ?: ""}" }))

        register(SlashCommandDef("renamechat", description = "重命名当前会话", callback = { _, _ -> "OK:renamechat" }))
        register(SlashCommandDef("getchatname", description = "返回当前会话名（管道）", callback = { _, _ -> "OK:getchatname" }))
        register(SlashCommandDef("setinput", description = "设置输入框文本并传给管道", rawQuotes = true, callback = { inv, _ -> inv.unnamedArgs.joinToString(" ") }))
        register(SlashCommandDef("bg", aliases = listOf("background"), description = "设置/清除/读取聊天背景", rawQuotes = true, callback = { inv, _ -> inv.unnamedArgs.joinToString(" ") }))
        register(SlashCommandDef("impersonate", aliases = listOf("imp"), description = "触发冒充生成（prompt=可选冒充提示）", rawQuotes = true, callback = { inv, _ -> "OK:impersonate:${inv.unnamedArgs.joinToString(" ")}" }))

        // ---- 消息类命令（官方 slash-commands.js；真正执行由 App SlashMessageActions 注入，引擎占位）----
        register(SlashCommandDef("send", description = "以用户身份发送消息", rawQuotes = true, callback = { inv, _ -> "OK:send:${inv.namedArgs["name"] ?: ""}:${inv.unnamedArgs.joinToString(" ")}" }))
        register(SlashCommandDef("sysname", description = "设置本会话旁白显示名", callback = { inv, _ -> "OK:sysname:${inv.unnamedArgs.joinToString(" ")}" }))
        register(SlashCommandDef("comment", description = "发送一条评论消息", rawQuotes = true, callback = { inv, _ -> "OK:comment:${inv.unnamedArgs.joinToString(" ")}" }))
        register(SlashCommandDef("message-role", description = "获取/设置消息角色（user/assistant/system）", callback = { inv, _ -> "OK:message-role:${inv.namedArgs["at"] ?: ""}:${inv.unnamedArgs.joinToString(" ")}" }))
        register(SlashCommandDef("message-name", description = "获取/设置消息显示名", callback = { inv, _ -> "OK:message-name:${inv.namedArgs["at"] ?: ""}:${inv.unnamedArgs.joinToString(" ")}" }))
        register(SlashCommandDef("hide", description = "隐藏消息（不进提示词）", callback = { inv, _ -> "OK:hide:${inv.unnamedArgs.joinToString(" ")}:name=${inv.namedArgs["name"] ?: ""}" }))
        register(SlashCommandDef("unhide", description = "取消隐藏消息", callback = { inv, _ -> "OK:unhide:${inv.unnamedArgs.joinToString(" ")}:name=${inv.namedArgs["name"] ?: ""}" }))
        register(SlashCommandDef("delname", aliases = listOf("cancel"), description = "删除指定名字的全部消息", callback = { inv, _ -> "OK:delname:${inv.unnamedArgs.joinToString(" ")}" }))
        register(SlashCommandDef("addswipe", aliases = listOf("swipeadd"), description = "给最后一条 AI 消息追加变体", callback = { inv, _ -> "OK:addswipe:${inv.namedArgs["switch"] ?: "false"}:${inv.unnamedArgs.joinToString(" ")}" }))
        register(SlashCommandDef("delswipe", aliases = listOf("swipedel"), description = "删除最后一条 AI 消息的变体（1 起；缺省删当前）", callback = { inv, _ -> "OK:delswipe:${inv.unnamedArgs.firstOrNull() ?: ""}" }))

        // ---- 脚本控制（variables.js /if：then/else 已由 SlashEngine 预解析为文本）----
        register(
            SlashCommandDef(
                "if",
                description = "条件比较：left/right/rule + 无名 then + else= 命名参数（闭包已由 SlashEngine 预解析）",
                callback = { inv, _ ->
                    val left = inv.namedArgs["left"] ?: ""
                    val right = inv.namedArgs["right"] ?: ""
                    val rule = inv.namedArgs["rule"] ?: "eq"
                    val then = inv.unnamedArgs.joinToString(" ")
                    val els = inv.namedArgs["else"] ?: ""
                    if (evalBoolean(rule, left, right)) then else els
                },
            ),
        )

        // ---- 官方常用纯函数命令（slash-commands.js / variables.js 语义）----

        register(
            SlashCommandDef(
                "upper",
                aliases = listOf("uppercase", "to-upper"),
                description = "转大写",
                callback = { inv, _ -> inv.unnamedArgs.joinToString(" ").uppercase() },
            ),
        )
        register(
            SlashCommandDef(
                "lower",
                aliases = listOf("lowercase", "to-lower"),
                description = "转小写",
                callback = { inv, _ -> inv.unnamedArgs.joinToString(" ").lowercase() },
            ),
        )
        register(
            SlashCommandDef(
                "substr",
                aliases = listOf("substring"),
                description = "取子串（start/end，支持负数，对齐 JS slice）",
                callback = { inv, _ ->
                    val text = inv.unnamedArgs.joinToString(" ")
                    sliceText(text, inv.namedArgs["start"]?.toIntOrNull(), inv.namedArgs["end"]?.toIntOrNull())
                },
            ),
        )
        register(
            SlashCommandDef(
                "replace",
                aliases = listOf("re"),
                description = "替换文本（mode=literal|regex，pattern/replacer 命名参数）",
                callback = { inv, _ -> replaceText(inv) },
            ),
        )
        register(
            SlashCommandDef(
                "trimstart",
                description = "裁剪到第一句完整句子的开头",
                callback = { inv, _ -> VectorTextUtils.trimToStartSentence(inv.unnamedArgs.joinToString(" ")) },
            ),
        )
        register(
            SlashCommandDef(
                "trimend",
                description = "裁剪到最后一句完整句子的结尾",
                callback = { inv, _ -> VectorTextUtils.trimToEndSentence(inv.unnamedArgs.joinToString(" ")) },
            ),
        )
        register(
            SlashCommandDef(
                "tokens",
                description = "统计文本 token 数（cl100k 近似，Claude/Gemini 官方 web tokenizer 不在范围）",
                callback = { inv, _ -> TokenCounterFactory.forModel("").count(inv.unnamedArgs.joinToString(" ")).toString() },
            ),
        )
        register(
            SlashCommandDef(
                "add",
                description = "多个值相加",
                splitUnnamedArgument = true,
                callback = { inv, _ -> numericFold(inv.unnamedArgs) { a, b -> a + b } },
            ),
        )
        register(
            SlashCommandDef(
                "sub",
                description = "第一个值减其余值",
                splitUnnamedArgument = true,
                callback = { inv, _ -> numericFold(inv.unnamedArgs) { a, b -> a - b } },
            ),
        )
        register(
            SlashCommandDef(
                "mul",
                description = "多个值相乘",
                splitUnnamedArgument = true,
                callback = { inv, _ -> numericFold(inv.unnamedArgs) { a, b -> a * b } },
            ),
        )
        register(
            SlashCommandDef(
                "div",
                description = "第一个值除以其余值",
                splitUnnamedArgument = true,
                callback = { inv, _ -> numericFold(inv.unnamedArgs) { a, b -> if (b == 0.0) 0.0 else a / b } },
            ),
        )
        register(
            SlashCommandDef(
                "mod",
                description = "第一个值取模其余值",
                splitUnnamedArgument = true,
                callback = { inv, _ -> numericFold(inv.unnamedArgs) { a, b -> if (b == 0.0) 0.0 else a % b } },
            ),
        )
        register(
            SlashCommandDef(
                "pow",
                description = "第一个值的其余值次方",
                splitUnnamedArgument = true,
                callback = { inv, _ -> numericFold(inv.unnamedArgs) { a, b -> a.pow(b) } },
            ),
        )
        register(
            SlashCommandDef(
                "max",
                description = "取最大值",
                splitUnnamedArgument = true,
                callback = { inv, _ -> numericFold(inv.unnamedArgs) { a, b -> max(a, b) } },
            ),
        )
        register(
            SlashCommandDef(
                "min",
                description = "取最小值",
                splitUnnamedArgument = true,
                callback = { inv, _ -> numericFold(inv.unnamedArgs) { a, b -> min(a, b) } },
            ),
        )
        register(
            SlashCommandDef(
                "abs",
                description = "取绝对值",
                splitUnnamedArgument = true,
                callback = { inv, _ -> numericFold(inv.unnamedArgs) { a, _ -> abs(a) } },
            ),
        )
        register(
            SlashCommandDef(
                "sqrt",
                description = "取平方根",
                splitUnnamedArgument = true,
                callback = { inv, _ -> numericFold(inv.unnamedArgs) { a, _ -> sqrt(a) } },
            ),
        )
        register(
            SlashCommandDef(
                "round",
                description = "四舍五入",
                splitUnnamedArgument = true,
                callback = { inv, _ -> numericFold(inv.unnamedArgs) { a, _ -> a.roundToInt().toDouble() } },
            ),
        )
    }

    /** 对齐 variables.js evalBoolean（eq/neq/in/nin/gt/gte/lt/lte/not）。 */
    private fun evalBoolean(rule: String, left: String, right: String): Boolean {
        val l = left
        val r = right
        return when (rule.lowercase()) {
            "eq" -> l == r
            "neq" -> l != r
            "in" -> l.contains(r)
            "nin" -> !l.contains(r)
            "gt" -> (l.toDoubleOrNull() ?: Double.NaN) > (r.toDoubleOrNull() ?: Double.NaN)
            "gte" -> (l.toDoubleOrNull() ?: Double.NaN) >= (r.toDoubleOrNull() ?: Double.NaN)
            "lt" -> (l.toDoubleOrNull() ?: Double.NaN) < (r.toDoubleOrNull() ?: Double.NaN)
            "lte" -> (l.toDoubleOrNull() ?: Double.NaN) <= (r.toDoubleOrNull() ?: Double.NaN)
            "not" -> l.isBlank()
            else -> false
        }
    }

    private fun addVariable(current: String?, value: String): String {
        if (current != null && current.trimStart().startsWith("[")) {
            return runCatching {
                val arr = kotlinx.serialization.json.Json.parseToJsonElement(current).jsonArray.toMutableList()
                arr += kotlinx.serialization.json.JsonPrimitive(value)
                kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), kotlinx.serialization.json.JsonArray(arr))
            }.getOrDefault(current + value)
        }
        val cur = current?.toDoubleOrNull()
        val inc = value.toDoubleOrNull()
        return if (cur != null && inc != null) {
            val sum = cur + inc
            if (sum == sum.toLong().toDouble()) sum.toLong().toString() else sum.toString()
        } else {
            (current ?: "") + value
        }
    }

    private fun sliceText(text: String, start: Int?, end: Int?): String {
        if (text.isEmpty()) return ""
        val n = text.length
        fun norm(i: Int?): Int? = when {
            i == null -> null
            i < 0 -> (n + i).coerceAtLeast(0)
            else -> i.coerceAtMost(n)
        }
        val s = norm(start) ?: 0
        val e = norm(end) ?: n
        return if (e <= s) "" else text.substring(s, e)
    }

    private fun replaceText(inv: CommandInvocation): String {
        val text = inv.unnamedArgs.joinToString(" ")
        val pattern = inv.namedArgs["pattern"] ?: return text
        if (pattern.isEmpty()) return text
        val replacer = inv.namedArgs["replacer"] ?: ""
        return when (inv.namedArgs["mode"] ?: "literal") {
            "literal" -> text.replace(pattern, replacer)
            "regex" -> {
                val regex = WorldRegexUtils.parse(pattern) ?: runCatching { Regex(pattern) }.getOrNull()
                    ?: return text
                regex.replace(text, replacer)
            }
            else -> text
        }
    }

    private fun numericFold(values: List<String>, op: (Double, Double) -> Double): String {
        val nums = values.mapNotNull { it.toDoubleOrNull() }
        if (nums.isEmpty()) return ""
        val result = nums.drop(1).fold(nums.first(), op)
        return if (result == result.toLong().toDouble()) result.toLong().toString() else result.toString()
    }
}
