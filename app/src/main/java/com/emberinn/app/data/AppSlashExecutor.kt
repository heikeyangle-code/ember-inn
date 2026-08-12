package com.emberinn.app.data

import com.emberinn.engine.slash.SlashCommandDef
import com.emberinn.engine.slash.SlashCommandResolver
import com.emberinn.engine.slash.SlashEngine
import com.emberinn.engine.slash.SlashRegistry
import com.emberinn.engine.slash.SlashState

/**
 * 消息类斜杠命令需要的 App 能力（ChatViewModel 实现；纯接口便于单测）。
 * 对齐官方 slash-commands.js：sendas/send/sys/sysname/comment/message-role/message-name/
 * hide/unhide/delname/addswipe/delswipe。UI 已有按钮的功能（继续/重生成/滑动/停止/人设/模型）不在此列。
 */
interface SlashMessageActions {
    /** /sendas：以指定角色插入一条消息（不触发生成；at=插入位，avatar=头像覆盖，compact=紧凑布局）。 */
    fun sendAsCharacter(name: String, text: String, at: Int?, avatar: String?, compact: Boolean): String
    /** /send：以用户身份插入一条消息（不触发生成；name=显示名，at=插入位，compact=紧凑布局）。 */
    fun sendAsUser(text: String, name: String?, at: Int?, compact: Boolean): String
    /** /sys：插入旁白消息（name 空则用会话旁白名/System）。 */
    fun sendSystemMessage(text: String, name: String): String
    /** /sysname：设置会话旁白显示名（空=重置为 System）。 */
    fun setNarratorName(name: String): String
    /** /comment：插入评论消息。 */
    fun sendComment(text: String): String
    /** /message-role：at 负数=倒数；role 空=只读返回当前角色。 */
    fun getSetMessageRole(at: Int, role: String): String
    /** /message-name：name 空=只读返回当前名字。 */
    fun getSetMessageName(at: Int, name: String): String
    /** /hide /unhide：index 为 -1 时取最后一条。 */
    fun hideMessage(index: Int, hidden: Boolean): String
    /** /delname：删除指定名字的全部消息；返回删除条数。 */
    fun deleteMessagesByName(name: String): Int

    /** 命令的提示信息（如 /delname 结果）；空则不提示。 */
    fun notify(text: String)
    /** /addswipe：给最后一条 AI 消息追加变体；返回新 swipe_id 文本。 */
    fun addSwipe(text: String, switch: Boolean): String
    /** /delswipe：id 1 起，null 删当前；返回新当前 swipe_id 文本。 */
    fun deleteSwipe(id: Int?): String
    /** /renamechat：重命名当前会话；空名返回提示文本。 */
    fun renameChat(name: String): String
    /** /getchatname：返回当前会话名（进管道）。 */
    fun chatName(): String
    /** /setinput：设置输入框文本，并把文本传给管道。 */
    fun setInput(text: String): String
    /** /bg：无参返回当前背景；clear 清除；否则设置背景（URL/路径）。 */
    fun setBackground(text: String): String
    /** /impersonate：触发冒充生成（prompt 可选，官方 quiet_prompt）；返回提示文本。 */
    fun impersonate(prompt: String): String
    /** /continue：继续最后一条消息（prompt 可选，官方 quiet_prompt+quietToLoud）。 */
    suspend fun continueChat(prompt: String): String
    /** /regenerate：重新生成最后一条 AI 回复。 */
    suspend fun regenerateChat(): String
    /** /swipe：direction=left/right 切换最后一条 AI 回复（右越界生成新变体）。 */
    suspend fun swipeChat(direction: String): String
    /** /persona-set：mode=lookup/temp/all（官方 setNameCallback；默认 all）。 */
    fun selectPersona(name: String, mode: String): String
    /** /preset：精确匹配选择 OpenAI 采样预设（官方 presetCommandCallback exact；fuzzy 用子串近似登记）；无参返回当前名。 */
    fun applyPreset(name: String): String
    /** /trigger：触发一次生成（官方 Generate('normal')；最后用户消息→generate，最后 AI→continue）。 */
    suspend fun triggerGeneration(await: Boolean = false): String
    /** /inject：写/删 chat_metadata.script_injects 并注入本会话生成；返回注入 ID。 */
    fun injectScript(text: String, id: String, position: String, depth: Int, role: String, scan: Boolean, ephemeral: Boolean, filter: String? = null): String
    /** /gen：用当前聊天上下文 + 提示生成文本（不落盘），返回生成文本（官方 generateCallback）。 */
    suspend fun generateText(prompt: String, length: Int?): String
    /** /genraw：直接用提示请求（system/prefill/length 可选），返回生成文本（官方 generateRawCallback）。 */
    suspend fun generateRaw(
        prompt: String,
        system: String,
        prefill: String,
        length: Int?,
        instruct: Boolean = true,
        asRole: String = "system",
        stop: String = "[]",
        trim: Boolean = true,
    ): String
    /** /summarize：无文本=总结当前聊天；有文本=按指定 source/prompt 总结（官方 summarizeCallback）。 */
    suspend fun summarize(text: String, source: String?, prompt: String?, quiet: Boolean): String
}

/**
 * App 斜杠执行器：消息类命令（真实动作）+ 引擎纯函数命令。
 * 解析器/闭包/flags 仍是引擎 SlashEngine 1:1，这里只注入命令回调。
 */
class AppSlashExecutor(private val actions: SlashMessageActions) : SlashCommandResolver {

    private val messageCommands = listOf(
        SlashCommandDef(
            "sendas",
            description = "以指定角色发送消息（官方 sendas：name 缺省用当前角色；at/avatar/compact/return）",
            rawQuotes = true,
            callback = { inv, _ ->
                actions.sendAsCharacter(
                    inv.namedArgs["name"]?.trim().orEmpty(),
                    inv.unnamedArgs.joinToString(" "),
                    atOf(inv.namedArgs["at"]),
                    inv.namedArgs["avatar"]?.trim().orEmpty(),
                    isTrue(inv.namedArgs["compact"]),
                )
            },
        ),
        SlashCommandDef(
            "send",
            description = "以用户身份发送消息（官方 send：不触发生成；name/at/compact/return）",
            rawQuotes = true,
            callback = { inv, _ ->
                actions.sendAsUser(
                    inv.unnamedArgs.joinToString(" "),
                    inv.namedArgs["name"]?.trim(),
                    atOf(inv.namedArgs["at"]),
                    isTrue(inv.namedArgs["compact"]),
                )
            },
        ),
        SlashCommandDef(
            "sys",
            aliases = listOf("nar"),
            description = "以系统/旁白身份发送消息（name= 可选）",
            rawQuotes = true,
            callback = { inv, _ ->
                actions.sendSystemMessage(inv.unnamedArgs.joinToString(" "), inv.namedArgs["name"]?.trim().orEmpty())
            },
        ),
        SlashCommandDef(
            "sysname",
            description = "设置本会话旁白显示名（空=重置为 System）",
            callback = { inv, _ -> actions.setNarratorName(inv.unnamedArgs.joinToString(" ")) },
        ),
        SlashCommandDef(
            "comment",
            description = "发送一条评论消息",
            rawQuotes = true,
            callback = { inv, _ -> actions.sendComment(inv.unnamedArgs.joinToString(" ")) },
        ),
        SlashCommandDef(
            "message-role",
            description = "获取/设置消息角色（user/assistant/system；at= 负数=倒数）",
            callback = { inv, _ ->
                actions.getSetMessageRole(
                    atOf(inv.namedArgs["at"]) ?: -1,
                    inv.unnamedArgs.joinToString(" "),
                )
            },
        ),
        SlashCommandDef(
            "message-name",
            description = "获取/设置消息显示名（at= 负数=倒数）",
            callback = { inv, _ ->
                actions.getSetMessageName(
                    atOf(inv.namedArgs["at"]) ?: -1,
                    inv.unnamedArgs.joinToString(" "),
                )
            },
        ),
        SlashCommandDef(
            "hide",
            description = "隐藏消息（不进提示词；支持范围起始下标）",
            callback = { inv, _ ->
                actions.hideMessage(rangeStart(inv.unnamedArgs.firstOrNull()), true)
            },
        ),
        SlashCommandDef(
            "unhide",
            description = "取消隐藏消息",
            callback = { inv, _ ->
                actions.hideMessage(rangeStart(inv.unnamedArgs.firstOrNull()), false)
            },
        ),
        SlashCommandDef(
            "delname",
            aliases = listOf("cancel"),
            description = "删除指定名字的全部消息",
            callback = { inv, _ ->
                val count = actions.deleteMessagesByName(inv.unnamedArgs.joinToString(" "))
                if (count > 0) actions.notify("已删除 $count 条消息")
                ""
            },
        ),
        SlashCommandDef(
            "addswipe",
            aliases = listOf("swipeadd"),
            description = "给最后一条 AI 消息追加变体（switch=true 立即切换）",
            callback = { inv, _ ->
                actions.addSwipe(inv.unnamedArgs.joinToString(" "), isTrue(inv.namedArgs["switch"]))
            },
        ),
        SlashCommandDef(
            "delswipe",
            aliases = listOf("swipedel"),
            description = "删除最后一条 AI 消息的变体（1 起；缺省删当前）",
            callback = { inv, _ ->
                actions.deleteSwipe(inv.unnamedArgs.firstOrNull()?.toIntOrNull())
            },
        ),
        SlashCommandDef(
            "renamechat",
            description = "重命名当前会话（官方 renamechat）",
            callback = { inv, _ -> actions.renameChat(inv.unnamedArgs.joinToString(" ")) },
        ),
        SlashCommandDef(
            "getchatname",
            description = "返回当前会话名（官方 getchatname，进管道）",
            callback = { _, _ -> actions.chatName() },
        ),
        SlashCommandDef(
            "setinput",
            description = "设置输入框文本并传给管道（官方 setinput）",
            rawQuotes = true,
            callback = { inv, _ -> actions.setInput(inv.unnamedArgs.joinToString(" ")) },
        ),
        SlashCommandDef(
            "bg",
            aliases = listOf("background"),
            description = "设置/清除/读取聊天背景（官方 bg；无参=返回当前，clear=清除）",
            rawQuotes = true,
            callback = { inv, _ -> actions.setBackground(inv.unnamedArgs.joinToString(" ")) },
        ),
        SlashCommandDef(
            "impersonate",
            aliases = listOf("imp"),
            description = "触发冒充生成（官方 impersonate；prompt 可选）",
            rawQuotes = true,
            callback = { inv, _ -> actions.impersonate(inv.unnamedArgs.joinToString(" ")) },
        ),
        SlashCommandDef(
            "continue",
            aliases = listOf("cont"),
            description = "继续生成最后一条消息（官方 continue；prompt 可选）",
            rawQuotes = true,
            callback = { _, _ -> "" },
            suspendCallback = { inv, _ -> actions.continueChat(inv.unnamedArgs.joinToString(" ")) },
        ),
        SlashCommandDef(
            "regenerate",
            aliases = listOf("regen"),
            description = "重新生成最后一条 AI 回复（官方 regenerate）",
            callback = { _, _ -> "" },
            suspendCallback = { _, _ -> actions.regenerateChat() },
        ),
        SlashCommandDef(
            "swipe",
            description = "切换最后一条 AI 回复（官方 swipe：direction=left/right，默认 right）",
            callback = { _, _ -> "" },
            suspendCallback = { inv, _ -> actions.swipeChat(inv.namedArgs["direction"] ?: "right") },
        ),
        SlashCommandDef(
            "trigger",
            description = "触发一次生成（官方 trigger；await=true 等待生成结束）",
            callback = { _, _ -> "" },
            suspendCallback = { inv, _ -> actions.triggerGeneration(isTrue(inv.namedArgs["await"])) },
        ),
        SlashCommandDef(
            "gen",
            description = "用当前聊天上下文 + 提示生成文本（官方 gen；length= 可选；不落盘）",
            rawQuotes = true,
            callback = { _, _ -> "" },
            suspendCallback = { inv, _ ->
                actions.generateText(inv.unnamedArgs.joinToString(" "), inv.namedArgs["length"]?.toIntOrNull())
            },
        ),
        SlashCommandDef(
            "genraw",
            description = "直接用提示请求生成（官方 genraw；system/prefill/length= 可选；不落盘）",
            rawQuotes = true,
            callback = { _, _ -> "" },
            suspendCallback = { inv, _ ->
                actions.generateRaw(
                    prompt = inv.unnamedArgs.joinToString(" "),
                    system = inv.namedArgs["system"] ?: "",
                    prefill = inv.namedArgs["prefill"] ?: "",
                    length = inv.namedArgs["length"]?.toIntOrNull(),
                    instruct = inv.namedArgs["instruct"]?.lowercase() != "off",
                    asRole = inv.namedArgs["as"] ?: "system",
                    stop = inv.namedArgs["stop"] ?: "[]",
                    trim = inv.namedArgs["trim"]?.lowercase() != "off",
                )
            },
        ),
        SlashCommandDef(
            "inject",
            description = "注入提示文本（官方 inject：position=before/after/chat/none，depth，role，scan，ephemeral；返回注入 ID）",
            rawQuotes = true,
            callback = { inv, _ ->
                actions.injectScript(
                    text = inv.unnamedArgs.joinToString(" "),
                    id = inv.namedArgs["id"] ?: "",
                    position = inv.namedArgs["position"] ?: "after",
                    depth = inv.namedArgs["depth"]?.toIntOrNull() ?: 4,
                    role = inv.namedArgs["role"] ?: "system",
                    scan = isTrue(inv.namedArgs["scan"]),
                    ephemeral = isTrue(inv.namedArgs["ephemeral"]),
                    filter = inv.namedArgs["filter"],
                )
            },
        ),
        SlashCommandDef(
            "summarize",
            description = "总结文本；无文本=总结当前聊天（官方 summarize：source/prompt/quiet）",
            rawQuotes = true,
            callback = { _, _ -> "" },
            suspendCallback = { inv, _ ->
                actions.summarize(
                    text = inv.unnamedArgs.joinToString(" "),
                    source = inv.namedArgs["source"],
                    prompt = inv.namedArgs["prompt"],
                    quiet = isTrue(inv.namedArgs["quiet"]),
                )
            },
        ),
        SlashCommandDef(
            "persona-set",
            aliases = listOf("persona"),
            description = "切换人设（官方 persona-set：mode=lookup/temp/all，默认 all）",
            callback = { inv, _ ->
                actions.selectPersona(inv.unnamedArgs.joinToString(" "), inv.namedArgs["mode"] ?: "all")
            },
        ),
        SlashCommandDef(
            "preset",
            description = "选择采样预设（官方 presetCommandCallback：精确匹配，fuzzy 子串近似；无参返回当前预设名）",
            rawQuotes = true,
            callback = { inv, _ ->
                actions.applyPreset(inv.unnamedArgs.joinToString(" ").trim())
            },
        ),
    )

    private val byName = buildMap<String, SlashCommandDef> {
        messageCommands.forEach { def ->
            put(def.name, def)
            def.aliases.forEach { put(it, def) }
        }
    }

    override fun resolve(name: String): SlashCommandDef? =
        byName[name.lowercase()] ?: SlashRegistry.resolve(name)

    /** 全部可执行命令清单（App 消息类优先，引擎命令兜底），供输入框斜杠补全 UI 使用。 */
    fun commandList(): List<Pair<String, String>> {
        val map = linkedMapOf<String, String>()
        byName.forEach { (_, def) -> map.putIfAbsent(def.name, def.description) }
        SlashRegistry.all().forEach { def -> map.putIfAbsent(def.name, def.description) }
        return map.toList()
    }

    fun execute(text: String, state: SlashState = SlashState()): String =
        SlashEngine.execute(text, state, this)

    suspend fun executeAsync(text: String, state: SlashState = SlashState()): String =
        SlashEngine.executeAsync(text, state, this)

    /** 官方 Number(args.at)：负数=从末尾倒数；-0 等价“末尾追加”，因此映射为 null（追加）。 */
    private fun atOf(raw: String?): Int? = raw?.toIntOrNull()?.let { if (raw == "-0") null else it }

    private fun rangeStart(raw: String?): Int = raw?.substringBefore('-')?.toIntOrNull() ?: -1

    private fun isTrue(v: String?): Boolean = v?.lowercase() in setOf("true", "on", "1", "yes", "y")
}
