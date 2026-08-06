package com.emberinn.engine.slash

import com.emberinn.engine.macros.SlashMacroState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** 一条斜杠命令的解析结果（对齐官方 SlashCommandExecutor 的核心字段）。 */
data class CommandInvocation(
    val name: String,
    val namedArgs: Map<String, String>,
    val namedLists: Map<String, List<String>> = emptyMap(),
    val unnamedArgs: List<String>,
    val raw: String,
)

/** 命令定义：name / aliases / 描述 / 执行回调。 */
data class SlashCommandDef(
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val callback: (CommandInvocation, SlashState) -> String,
)

class SlashParseException(message: String) : RuntimeException(message)

/** 斜杠执行状态：变量（/let）、参数（/qr-arg）、管道值（{{pipe}}）。 */
class SlashState : SlashMacroState {

    val variables = mutableMapOf<String, String>()
    val arguments = mutableMapOf<String, String>()
    var pipeValue: String = ""

    override fun variable(name: String): String? {
        val parts = name.split("::")
        val value = variables[parts[0]] ?: return null
        if (parts.size > 1) {
            val index = parts[1].toIntOrNull() ?: return null
            return runCatching {
                Json.parseToJsonElement(value).jsonArray
                    .getOrNull(index)
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull()
        }
        return value
    }

    override fun argument(name: String): String? = arguments[name]

    override fun pipe(): String? = pipeValue
}
