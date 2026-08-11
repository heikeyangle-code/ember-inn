package com.emberinn.engine.prompt

/**
 * 官方 script.js setExtensionPrompt / getExtensionPrompt / getExtensionPromptByName
 * 与 slash-commands.js injectCallback 的 1:1 移植。
 *
 * 边界登记：filter 闭包无法从元数据复活（官方 closureToFilter 依赖 SlashCommandParser，
 * App 不支持 /inject filter 参数，见 HANDOFF 3.4 剩余偏差）；ephemeral 生命周期在 App 层
 * （GENERATION_ENDED/STOPPED 后删除，官方 eventSource.once 语义）。
 */
object ExtensionPromptEngine {

    /** 官方 script.js extension_prompt_types。 */
    const val POSITION_NONE = -1
    const val POSITION_IN_PROMPT = 0
    const val POSITION_IN_CHAT = 1
    const val POSITION_BEFORE_PROMPT = 2

    /** 官方 script.js extension_prompt_roles。 */
    const val ROLE_SYSTEM = 0
    const val ROLE_USER = 1
    const val ROLE_ASSISTANT = 2

    const val DEFAULT_DEPTH = 4
    const val MAX_INJECTION_DEPTH = 10000

    /** 官方 slash-commands.js SCRIPT_PROMPT_KEY。 */
    const val SCRIPT_PROMPT_KEY = "script_inject_"

    /** 官方 extension_prompts[key] 项（filter 为 App 层闭包，此处不承载）。 */
    data class Entry(
        val key: String,
        val value: String,
        val position: Int,
        val depth: Int,
        val scan: Boolean,
        val role: Int,
    )

    /** chat_metadata.script_injects[id] 条目（官方 injectCallback 持久化字段）。 */
    data class ScriptInject(
        val id: String,
        val value: String,
        val position: Int = POSITION_IN_PROMPT,
        val depth: Int = DEFAULT_DEPTH,
        val role: Int = ROLE_SYSTEM,
        val scan: Boolean = false,
    )

    /** 一次生成前把 script_injects 全部落成官方扩展提示的规划。 */
    data class ScriptInjectionPlan(
        val extensionPrompts: Map<String, ExtensionPrompt>,
        val inChatPrompts: List<PromptItem>,
        val scanValues: List<String>,
    )

    /** 官方 setExtensionPrompt：key/value/position/depth/scan/role 原样入表。 */
    fun set(
        store: MutableMap<String, Entry>,
        key: String,
        value: String,
        position: Int,
        depth: Int,
        scan: Boolean = false,
        role: Int = ROLE_SYSTEM,
    ) {
        store[key] = Entry(key, value, position, depth, scan, role)
    }

    /**
     * 官方 getExtensionPrompt 纯逻辑：
     * position/depth/role 过滤 → trim + separator 拼接 → wrap 首尾补 separator → substituteParams。
     * filter 函数官方可能异步返回 false；引擎侧由调用方先过滤好（App 不支持 filter 闭包）。
     */
    fun get(
        entries: Map<String, Entry>,
        position: Int = POSITION_IN_PROMPT,
        depth: Int? = null,
        separator: String = "\n",
        role: Int? = null,
        wrap: Boolean = true,
        substitute: (String) -> String = { it },
    ): String {
        // 官方 getExtensionPrompt：Object.keys(extension_prompts).sort() 后按 key 升序取 value
        var values = entries.toSortedMap().values
            .filter { it.position == position && it.value.isNotEmpty() }
            .filter { depth == null || it.depth == depth }
            .filter { role == null || it.role == role }
            .map { it.value.trim() }
            .joinToString(separator)
        if (wrap && values.isNotEmpty() && !values.startsWith(separator)) values = separator + values
        if (wrap && values.isNotEmpty() && !values.endsWith(separator)) values = values + separator
        if (values.isNotEmpty()) values = substitute(values)
        return values
    }

    /** 官方 getExtensionPromptByName：单条 value 宏替换（世界书 scan 注入用）。 */
    fun getByName(
        entries: Map<String, Entry>,
        key: String,
        substitute: (String) -> String = { it },
    ): String {
        if (key.isEmpty()) return ""
        val prompt = entries[key] ?: return ""
        return substitute(prompt.value)
    }

    /**
     * 官方 injectCallback 的参数解析（slash-commands.js:3778-3824 逐字语义）：
     * positions before→2 / after→0 / chat→1 / none→-1，未知回退 after；
     * depth = Number(raw ?? 4)，NaN 回退 4；role 字符串 lower/trim 后查表，其余一律 SYSTEM；
     * value = raw || ''；空 value 不产生注入（官方同时 delete metadata 条目）。
     */
    fun parseInject(
        idRaw: String?,
        valueRaw: String?,
        positionRaw: Any?,
        depthRaw: Any?,
        roleRaw: Any?,
        scanRaw: Boolean,
    ): ScriptInject {
        val positions = mapOf(
            "before" to POSITION_BEFORE_PROMPT,
            "after" to POSITION_IN_PROMPT,
            "chat" to POSITION_IN_CHAT,
            "none" to POSITION_NONE,
        )
        val roles = mapOf(
            "system" to ROLE_SYSTEM,
            "user" to ROLE_USER,
            "assistant" to ROLE_ASSISTANT,
        )
        val defaultPosition = "after"
        val id = (idRaw ?: "").ifEmpty { randomId() }
        val position = when {
            // 官方元数据里存的是数字枚举（injectCallback 已转好）；字符串只出现在命令参数
            positionRaw is Int -> positionRaw
            positionRaw is Long -> positionRaw.toInt()
            positionRaw is Double -> positionRaw.toInt()
            positionRaw is String -> positions[positionRaw] ?: positions.getValue(defaultPosition)
            else -> positions[positionRaw ?: defaultPosition] ?: positions.getValue(defaultPosition)
        }
        val depth = numberOr(depthRaw ?: DEFAULT_DEPTH, DEFAULT_DEPTH)
        val role = when {
            roleRaw is String -> roles[roleRaw.lowercase().trim()] ?: ROLE_SYSTEM
            roleRaw is Int -> if (roleRaw == ROLE_USER || roleRaw == ROLE_ASSISTANT) roleRaw else ROLE_SYSTEM
            roleRaw is Long -> if (roleRaw.toInt() == ROLE_USER || roleRaw.toInt() == ROLE_ASSISTANT) roleRaw.toInt() else ROLE_SYSTEM
            else -> ROLE_SYSTEM
        }
        return ScriptInject(
            id = id,
            value = valueRaw ?: "",
            position = position,
            depth = depth,
            role = role,
            scan = scanRaw,
        )
    }

    /**
     * 一次生成前：官方 processChatSlashCommands 把 chat_metadata.script_injects
     * 逐条 setExtensionPrompt；这里产出引擎侧三种落点：
     * before→start 扩展提示、after→end 扩展提示、chat→in-chat PromptItem、none→不注入；
     * scan=true 的 value 按官方 getExtensionPromptByName（宏替换后）进世界书扫描缓冲。
     */
    fun planScriptInjections(
        injects: List<ScriptInject>,
        substitute: (String) -> String = { it },
    ): ScriptInjectionPlan {
        val extensionPrompts = mutableMapOf<String, ExtensionPrompt>()
        val inChatPrompts = mutableListOf<PromptItem>()
        val scanValues = mutableListOf<String>()
        for (inject in injects) {
            if (inject.value.isEmpty()) continue
            val key = SCRIPT_PROMPT_KEY + inject.id
            val roleName = roleName(inject.role)
            if (inject.scan) {
                scanValues += substitute(inject.value)
            }
            when (inject.position) {
                POSITION_BEFORE_PROMPT -> extensionPrompts[key] =
                    ExtensionPrompt(key, roleName, inject.value, "start", inject.depth)
                POSITION_IN_PROMPT -> extensionPrompts[key] =
                    ExtensionPrompt(key, roleName, inject.value, "end", inject.depth)
                POSITION_IN_CHAT -> inChatPrompts += PromptItem(
                    identifier = key,
                    name = "脚本注入 ${inject.id}",
                    content = inject.value,
                    role = roleName,
                    injectionDepth = inject.depth,
                    injectionOrder = 100,
                )
                // POSITION_NONE：官方不注入提示词（仅存元数据 + scan）
            }
        }
        return ScriptInjectionPlan(extensionPrompts, inChatPrompts, scanValues)
    }

    /** 官方 getExtensionPromptRoleByName（string 分支；数字/未知一律 SYSTEM）。 */
    fun roleByName(roleName: String?): Int = when (roleName?.lowercase()?.trim()) {
        "system" -> ROLE_SYSTEM
        "user" -> ROLE_USER
        "assistant" -> ROLE_ASSISTANT
        else -> ROLE_SYSTEM
    }

    /** role int → PromptItem.role 字符串。 */
    fun roleName(role: Int): String = when (role) {
        ROLE_USER -> "user"
        ROLE_ASSISTANT -> "assistant"
        else -> "system"
    }

    /** role 字符串 → 官方 role int（未知回退 system）。 */
    fun roleInt(role: String): Int = roleByName(role)

    /** 官方 Math.random().toString(36).substring(2) 的等价随机 id。 */
    fun randomId(): String {
        var n = Math.random()
        var s = ""
        while (n > 0) {
            val digit = (n * 36).toInt()
            s += "0123456789abcdefghijklmnopqrstuvwxyz"[digit]
            n = n * 36 - digit
        }
        return s.take(8)
    }

    /** 官方 Number(raw ?? fallback)，NaN 回退 fallback（Kotlin 侧 Int 入参原样返回）。 */
    private fun numberOr(raw: Any?, fallback: Int): Int = when (raw) {
        is Int -> raw
        is Long -> raw.toInt()
        is Double -> if (raw.isNaN()) fallback else raw.toInt()
        is Float -> if (raw.isNaN()) fallback else raw.toInt()
        is String -> raw.trim().toIntOrNull() ?: fallback
        null -> fallback
        else -> fallback
    }
}
