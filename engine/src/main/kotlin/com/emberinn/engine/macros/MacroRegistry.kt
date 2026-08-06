package com.emberinn.engine.macros

/** 自定义宏注册表（对齐官方 MacroRegistry.registerMacro 的最小核心）。 */
object MacroRegistry {

    private val handlers = linkedMapOf<String, (String, MacroEnv) -> String?>()

    fun register(name: String, handler: (String, MacroEnv) -> String?) {
        handlers[name.lowercase()] = handler
    }

    fun unregister(name: String) {
        handlers.remove(name.lowercase())
    }

    fun resolve(name: String, args: String, env: MacroEnv): String? =
        handlers[name.lowercase()]?.invoke(args, env)
}

/** 斜杠执行状态（宏侧接口，避免 macros 依赖 slash 包）。 */
interface SlashMacroState {
    fun variable(name: String): String?
    fun argument(name: String): String?
    fun pipe(): String?
}
