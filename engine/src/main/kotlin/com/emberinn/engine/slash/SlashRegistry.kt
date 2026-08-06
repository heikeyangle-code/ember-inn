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
        return def.callback(invocation)
    }

    fun all(): List<SlashCommandDef> = commands.values.distinctBy { it.name }

    init {
        register(SlashCommandDef("help", description = "列出可用命令") {
            all().joinToString("\n") { "/${it.name} — ${it.description}" }
        })
        register(SlashCommandDef("continue", description = "继续生成上一条消息") { "OK:continue" })
        register(SlashCommandDef("regenerate", description = "重新生成最后一条消息") { "OK:regenerate" })
        register(SlashCommandDef("swipe", description = "切换回复（可带方向）") { "OK:swipe:${it.unnamedArgs.firstOrNull() ?: ""}" })
        register(SlashCommandDef("sys", description = "以系统身份发送消息") { "OK:sys:${it.unnamedArgs.joinToString(" ")}" })
        register(SlashCommandDef("sendas", aliases = listOf("send"), description = "以指定角色发送消息") {
            "OK:sendas:${it.namedArgs["name"] ?: ""}:${it.unnamedArgs.joinToString(" ")}"
        })
        register(SlashCommandDef("echo", description = "原样返回无名参数") { it.unnamedArgs.joinToString(" ") })
        register(SlashCommandDef("pass", aliases = listOf("return"), description = "把文本传给下一条命令（管道透传）") {
            it.unnamedArgs.joinToString(" ")
        })
        register(SlashCommandDef("persona", description = "切换人设") {
            "OK:persona:${it.unnamedArgs.joinToString(" ")}:mode=${it.namedArgs["mode"] ?: "all"}"
        })
    }
}
