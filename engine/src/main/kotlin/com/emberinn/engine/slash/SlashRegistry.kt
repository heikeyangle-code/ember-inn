package com.emberinn.engine.slash

/** 命令注册表 + 内置命令（对齐官方 registerSlashCommand 的核心）。 */
object SlashRegistry {

    private val commands = linkedMapOf<String, SlashCommandDef>()

    fun register(def: SlashCommandDef) {
        commands[def.name] = def
        def.aliases.forEach { commands[it] = def }
    }

    fun get(name: String): SlashCommandDef? = commands[name.lowercase()]

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
                callback = { inv, _ -> "OK:swipe:${inv.unnamedArgs.firstOrNull() ?: ""}" },
            ),
        )
        register(SlashCommandDef("sys", description = "以系统身份发送消息", callback = { inv, _ -> "OK:sys:${inv.unnamedArgs.joinToString(" ")}" }))
        register(
            SlashCommandDef(
                "sendas",
                aliases = listOf("send"),
                description = "以指定角色发送消息",
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
                description = "切换人设",
                callback = { inv, _ -> "OK:persona:${inv.unnamedArgs.joinToString(" ")}:mode=${inv.namedArgs["mode"] ?: "all"}" },
            ),
        )
        register(
            SlashCommandDef(
                "let",
                description = "设置作用域变量（对齐官方 /let）",
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
                "parser-flag",
                description = "解析器标志（引擎侧为无操作，参数保留）",
                callback = { inv, _ -> inv.unnamedArgs.joinToString(" ") },
            ),
        )
    }
}
