package com.emberinn.engine.prompt

import com.emberinn.engine.worldinfo.DepthEntry

/**
 * 官方 script.js generate 的深度提示注入（script.js:4418-4430 + 4609-4614）1:1 移植：
 * 群聊/角色卡深度提示 → setExtensionPrompt(DEPTH_PROMPT[_index], IN_CHAT, depth, role)；
 * 世界书 atDepth 条目 → flushWIInjections 后逐条 setExtensionPrompt(CUSTOM_WI_DEPTH_ROLE, IN_CHAT, depth, role)。
 * 产出引擎 PromptItem（injectionOrder=100，官方 populationInjectionPrompts 的扩展合并序）。
 */
object DepthPromptEngine {

    const val DEPTH_PROMPT_ID = "DEPTH_PROMPT"
    const val DEFAULT_DEPTH = 4
    const val DEFAULT_ROLE = "system"

    /** 官方 inject_ids.CUSTOM_WI_DEPTH_ROLE(depth, role)：role 用官方 int（system=0/user=1/assistant=2）。 */
    fun worldInfoDepthIdentifier(depth: Int, role: String): String =
        "customDepthWI_${depth}_${ExtensionPromptEngine.roleInt(role)}"

    /** 角色卡深度提示：官方 setExtensionPrompt 连空 value 也入表（getExtensionPrompt 再过滤空值）。 */
    fun characterDepthPromptItem(
        content: String,
        depth: Int = DEFAULT_DEPTH,
        role: String = DEFAULT_ROLE,
    ): PromptItem = PromptItem(
        identifier = DEPTH_PROMPT_ID,
        name = "深度提示",
        content = content,
        role = role,
        injectionDepth = depth,
        injectionOrder = 100,
    )

    /** 群聊深度提示（官方 groupDepthPrompts.forEach → DEPTH_PROMPT_${index}）。 */
    fun groupDepthPromptItems(
        prompts: List<PromptItem>,
    ): List<PromptItem> = prompts.mapIndexed { index, p ->
        p.copy(
            identifier = "DEPTH_PROMPT_$index",
            name = "群聊深度提示 ${index + 1}",
            injectionDepth = p.injectionDepth ?: DEFAULT_DEPTH,
            injectionOrder = 100,
        )
    }

    /** 世界书 atDepth 条目 → in-chat 深度提示（官方 identifier 含 role int）。 */
    fun worldInfoDepthPromptItems(depthEntries: List<DepthEntry>): List<PromptItem> =
        depthEntries.map { d ->
            PromptItem(
                identifier = worldInfoDepthIdentifier(d.depth, d.role),
                name = "世界书深度 ${d.depth}/${d.role}",
                content = d.entries.joinToString("\n"),
                role = d.role,
                injectionDepth = d.depth,
                injectionOrder = 100,
            )
        }
}
