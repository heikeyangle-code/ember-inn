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
    /** 解析结束位置（差分用，对齐官方 SlashCommandExecutor.end）。 */
    val endIndex: Int = 0,
)

/** 命令定义：name / aliases / 描述 / 执行回调。 */
data class SlashCommandDef(
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val callback: (CommandInvocation, SlashState) -> String,
    /** 异步命令（/gen /genraw 等）：executeAsync 优先走这里；execute 同步路径 runBlocking 兜底。 */
    val suspendCallback: (suspend (CommandInvocation, SlashState) -> String)? = null,
    val rawQuotes: Boolean = false,
    /** 对齐官方 splitUnnamedArgument：无名参数按空白拆成多个（如 /qr-arg、/let）。 */
    val splitUnnamedArgument: Boolean = false,
    /** 对齐官方 splitUnnamedArgumentCount：前 N 个拆开，其余合并为一个值。 */
    val splitUnnamedArgumentCount: Int? = null,
)

class SlashParseException(message: String) : RuntimeException(message)

/** 斜杠执行状态：变量（/let）、参数（/qr-arg）、管道值（{{pipe}}）、解析器标志。 */
class SlashState : SlashMacroState {

    val variables = mutableMapOf<String, String>()
    val arguments = mutableMapOf<String, String>()
    var pipeValue: String = ""
    /** 对齐官方 PARSER_FLAG.STRICT_ESCAPING（/parser-flag 可切换，默认关）。 */
    var strictEscaping: Boolean = false
    /** 对齐官方 PARSER_FLAG.REPLACE_GETVAR（新宏引擎下宏展开由 MacroEngine 完成，仅记录状态）。 */
    var replaceGetvar: Boolean = false

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
