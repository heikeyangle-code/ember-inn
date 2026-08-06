package com.emberinn.engine.prompt

/** 人设（对齐官方 persona：描述 + 注入位置/角色/深度）。 */
data class Persona(
    val name: String,
    val description: String,
    val position: Int = StoryStringPosition.IN_PROMPT,
    val role: String = "system",
    val depth: Int = 2,
)
