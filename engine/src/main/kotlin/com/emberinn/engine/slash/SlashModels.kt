package com.emberinn.engine.slash

/** 一条斜杠命令的解析结果（对齐官方 SlashCommandExecutor 的核心字段）。 */
data class CommandInvocation(
    val name: String,
    val namedArgs: Map<String, String>,
    val unnamedArgs: List<String>,
    val raw: String,
)

/** 命令定义：name / aliases / 描述 / 执行回调。 */
data class SlashCommandDef(
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val callback: (CommandInvocation) -> String,
)

class SlashParseException(message: String) : RuntimeException(message)
