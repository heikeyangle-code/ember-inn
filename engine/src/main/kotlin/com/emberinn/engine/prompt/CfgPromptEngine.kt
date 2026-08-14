package com.emberinn.engine.prompt

import kotlinx.serialization.json.jsonPrimitive

/**
 * 官方 CFG Scale 扩展纯逻辑（scripts/cfg-scale.js 1:1）：
 * getGuidanceScale 优先级（chat > chara > global）+ getCfgPrompt（正/负提示按类型与合并勾选 unshift 拼接）
 * + getCustomSeparator（JSON.parse 失败回退换行）+ 插入深度。
 * 差分：scripts/diff/cfg-prompt-official.mjs（25 例）。
 * 边界（登记）：groupchatCharOverride=true 且 charaCfg 不存在时官方 JS 会抛 TypeError（读 undefined）；
 * Kotlin 空安全返回 value=null 的 chara 档；真实群聊总存在角色配置，不触发。
 */
object CfgPromptEngine {

    const val CFG_TYPE_CHAT = 0
    const val CFG_TYPE_CHARA = 1
    const val CFG_TYPE_GLOBAL = 2

    /** 全局 CFG（官方 extension_settings.cfg.global）。 */
    data class CfgGlobal(
        val guidanceScale: Double = 1.0,
        val negativePrompt: String = "",
        val positivePrompt: String = "",
    )

    /** 角色 CFG（官方 extension_settings.cfg.chara 按角色文件名）。 */
    data class CfgChara(
        val name: String,
        val guidanceScale: Double? = null,
        val negativePrompt: String = "",
        val positivePrompt: String = "",
    )

    /** 会话 CFG（官方 chat_metadata.cfg_* 键）。 */
    data class CfgChat(
        val guidanceScale: Double? = null,
        val negativePrompt: String = "",
        val positivePrompt: String = "",
        /** 官方 cfg_prompt_combine：勾选来源（0=chat/1=chara/2=global）。 */
        val promptCombine: List<Int> = emptyList(),
        /** 官方 cfg_groupchat_individual_chars。 */
        val groupchatIndividualChars: Boolean = false,
        /** 官方 cfg_prompt_insertion_depth，UI 恒写 Number。 */
        val promptInsertionDepth: Int = 1,
        /** 官方 cfg_prompt_separator 原始 JSON 字符串（如 "\n"）；null/非法回退换行。 */
        val promptSeparator: String? = null,
    )

    data class GuidanceScale(val type: Int, val value: Double?)

    data class CfgPromptResult(val value: String, val depth: Int)

    /**
     * 官方 getGuidanceScale：chat 档 > chara 档 > global 档；guidance==1 视为不启用跳过。
     * chara 档条件 `(!selected_group && charaCfg || groupchatCharOverride) && charaCfg?.guidance_scale !== 1`
     * 原样保留（含 groupchatCharOverride 时无角色配置 → value=null 的空安全等价，官方此处抛 TypeError）。
     */
    fun getGuidanceScale(global: CfgGlobal, chara: CfgChara?, chat: CfgChat, selectedGroup: Boolean): GuidanceScale? {
        val chatGuidanceScale = chat.guidanceScale
        if (chatGuidanceScale != null && chatGuidanceScale != 1.0 && !chat.groupchatIndividualChars) {
            return GuidanceScale(CFG_TYPE_CHAT, chatGuidanceScale)
        }
        if (((!selectedGroup && chara != null) || chat.groupchatIndividualChars) && chara?.guidanceScale != 1.0) {
            return GuidanceScale(CFG_TYPE_CHARA, chara?.guidanceScale)
        }
        if (global.guidanceScale != 1.0) {
            return GuidanceScale(CFG_TYPE_GLOBAL, global.guidanceScale)
        }
        return null
    }

    /** 官方 getCustomSeparator：JSON.parse(prompt_separator)，失败回退 '\n'。 */
    fun getCustomSeparator(chat: CfgChat): String {
        val raw = chat.promptSeparator
        if (raw.isNullOrEmpty()) return "\n"
        return runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonPrimitive.content
        }.getOrDefault("\n")
    }

    /** 官方 getCfgPrompt：按 guidance 类型与 prompt_combine unshift 收集 → filter 非空 → join(separator)；depth 默认 1。 */
    fun getCfgPrompt(
        guidanceScale: GuidanceScale,
        isNegative: Boolean,
        chat: CfgChat,
        chara: CfgChara?,
        global: CfgGlobal,
        substitute: (String) -> String,
    ): CfgPromptResult {
        val split = mutableListOf<String>()
        if (guidanceScale.type == CFG_TYPE_CHAT || chat.promptCombine.contains(CFG_TYPE_CHAT)) {
            split.add(0, substitute(if (isNegative) chat.negativePrompt else chat.positivePrompt))
        }
        if (guidanceScale.type == CFG_TYPE_CHARA || chat.promptCombine.contains(CFG_TYPE_CHARA)) {
            // 官方 charaCfg 缺失时读 undefined → JS 抛错；Kotlin 空安全按空角色处理
            val c = chara ?: CfgChara("")
            split.add(0, substitute(if (isNegative) c.negativePrompt else c.positivePrompt))
        }
        if (guidanceScale.type == CFG_TYPE_GLOBAL || chat.promptCombine.contains(CFG_TYPE_GLOBAL)) {
            split.add(0, substitute(if (isNegative) global.negativePrompt else global.positivePrompt))
        }
        val separator = getCustomSeparator(chat)
        val combined = split.filter { it.isNotEmpty() }.joinToString(separator)
        return CfgPromptResult(combined, chat.promptInsertionDepth)
    }
}
