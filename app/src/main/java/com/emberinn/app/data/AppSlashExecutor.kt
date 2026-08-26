package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.slash.SlashCommandDef
import com.emberinn.engine.slash.SlashCommandResolver
import com.emberinn.engine.slash.SlashEngine
import com.emberinn.engine.slash.SlashRegistry
import com.emberinn.engine.slash.SlashState
import java.io.File

/**
 * 消息类斜杠命令需要的 App 能力（ChatViewModel 实现；纯接口便于单测）。
 * 对齐官方 slash-commands.js：sendas/send/sys/sysname/comment/message-role/message-name/
 * hide/unhide/delname/addswipe/delswipe。UI 已有按钮的功能（继续/重生成/滑动/停止/人设/模型）不在此列。
 */
/** /sendas、/send 的落盘结果：mes 供 return=pipe，json 供 return=object（官方 message 对象）。 */
data class ManualSendResult(val mes: String, val json: String)

interface SlashMessageActions {
    /** /sendas：以指定角色插入一条消息（不触发生成；at=插入位，avatar=头像覆盖，compact=紧凑布局）。 */
    fun sendAsCharacter(name: String, text: String, at: Int?, avatar: String?, compact: Boolean): ManualSendResult
    /** /send：以用户身份插入一条消息（不触发生成；name=显示名，at=插入位，compact=紧凑布局）。 */
    fun sendAsUser(text: String, name: String?, at: Int?, compact: Boolean): ManualSendResult
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
    /** /preset：选择 OpenAI 采样预设（官方 presetCommandCallback：exact + Fuse.js 7.1 模糊，引擎差分）；无参返回当前名。 */
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
    /** /translate：翻译文本；target/provider 缺省用扩展设置（官方 translate 扩展 callback）。 */
    suspend fun translateText(text: String, target: String?, provider: String?): String
    /** /speak：朗读文本（官方 tts 扩展 onNarrateText；voice=voiceMap 角色名，App 无 voiceMap 忽略）。 */
    fun speakText(text: String, voice: String?): String

    /** /db 子命令附件上下文：返回当前 (characterAvatar, chatFile, charName) 用于 attachments 三源定位。 */
    fun attachmentsContext(): Triple<String, String, String> = Triple("", "", "")
}

/**
 * App 斜杠执行器：消息类命令（真实动作）+ 引擎纯函数命令。
 * 解析器/闭包/flags 仍是引擎 SlashEngine 1:1，这里只注入命令回调。
 *
 * @param context 可选 Android Context；非空时扩展命令（/db /listGallery /installAsset
 *   /qr /expression /world /imagine）真接对应 service，为空（测试桩）则回退占位 stub。
 */
class AppSlashExecutor(
    private val actions: SlashMessageActions,
    private val context: Context? = null,
) : SlashCommandResolver {

    private val messageCommands = listOf(
        SlashCommandDef(
            "sendas",
            description = "以指定角色发送消息（官方 sendas：name 缺省用当前角色；at/avatar/compact/return）",
            rawQuotes = true,
            callback = { inv, _ ->
                returnReturn(
                    inv.namedArgs["return"],
                    actions.sendAsCharacter(
                        inv.namedArgs["name"]?.trim().orEmpty(),
                        inv.unnamedArgs.joinToString(" "),
                        atOf(inv.namedArgs["at"]),
                        inv.namedArgs["avatar"]?.trim().orEmpty(),
                        isTrue(inv.namedArgs["compact"]),
                    ),
                    actions,
                )
            },
        ),
        SlashCommandDef(
            "send",
            description = "以用户身份发送消息（官方 send：不触发生成；name/at/compact/return）",
            rawQuotes = true,
            callback = { inv, _ ->
                returnReturn(
                    inv.namedArgs["return"],
                    actions.sendAsUser(
                        inv.unnamedArgs.joinToString(" "),
                        inv.namedArgs["name"]?.trim(),
                        atOf(inv.namedArgs["at"]),
                        isTrue(inv.namedArgs["compact"]),
                    ),
                    actions,
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
                // 官方 isFalseBoolean：off/false/0 视为关
                actions.generateRaw(
                    prompt = inv.unnamedArgs.joinToString(" "),
                    system = inv.namedArgs["system"] ?: "",
                    prefill = inv.namedArgs["prefill"] ?: "",
                    length = inv.namedArgs["length"]?.toIntOrNull(),
                    instruct = !com.emberinn.engine.slash.SlashMathEngine.isFalseBoolean(inv.namedArgs["instruct"]),
                    asRole = inv.namedArgs["as"] ?: "system",
                    stop = inv.namedArgs["stop"] ?: "[]",
                    trim = !com.emberinn.engine.slash.SlashMathEngine.isFalseBoolean(inv.namedArgs["trim"]),
                )
            },
        ),
        SlashCommandDef(
            "inject",
            description = "注入提示文本（官方 inject：position=before/after/chat/none，depth，role，scan，ephemeral，filter=闭包；返回注入 ID）",
            rawQuotes = true,
            closureArgs = setOf("filter"),
            callback = { inv, _ ->
                // 官方 injectCallback：filter 参数解析失败（提供但为空）直接抛错
                if (inv.namedArgs.containsKey("filter") && inv.namedArgs["filter"].isNullOrBlank()) {
                    throw com.emberinn.engine.slash.SlashParseException("无法解析 filter 参数：必须是有效的非空闭包 {: ... :}")
                }
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
            "translate",
            description = "翻译文本（官方 translate 扩展：target=目标语言代码，provider=提供商；缺省用扩展设置）",
            rawQuotes = true,
            callback = { _, _ -> "" },
            suspendCallback = { inv, _ ->
                actions.translateText(
                    inv.unnamedArgs.joinToString(" "),
                    inv.namedArgs["target"],
                    inv.namedArgs["provider"],
                )
            },
        ),
        SlashCommandDef(
            "speak",
            aliases = listOf("narrate", "tts"),
            description = "朗读文本（官方 tts 扩展 /speak：voice=voiceMap 角色名；App 无 voiceMap，用当前朗读配置）",
            rawQuotes = true,
            callback = { _, _ -> "" },
            suspendCallback = { inv, _ ->
                actions.speakText(inv.unnamedArgs.joinToString(" "), inv.namedArgs["voice"])
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
            description = "选择采样预设（官方 presetCommandCallback：exact + Fuse.js 模糊；无参返回当前预设名）",
            rawQuotes = true,
            callback = { inv, _ ->
                actions.applyPreset(inv.unnamedArgs.joinToString(" ").trim())
            },
        ),
    )

    /**
     * 扩展命令（对照官方 extensions 注册；App 端通过 service 真接）。
     * context 非空时真调对应 service，为空（测试桩）则回退 SlashRegistry stub（"OK:..."）。
     * /summarize /vectorize /index /vectorize-faiss /caption /member 等仍走 SlashRegistry stub，
     * 因依赖 ChatRepository/群聊上下文，已在 actions 或后续接线。
     */
    private val extensionCommands: List<SlashCommandDef> = context?.let { ctx ->
        listOf(
            // attachments / databank（官方 /db 系列）
            SlashCommandDef(
                "db",
                description = "数据银行附件操作（sub=get/list/add/update/disable/enable/delete/show/hide/apply/list-inline/parse-inline）",
                callback = { inv, _ ->
                    val sub = inv.namedArgs["sub"] ?: inv.unnamedArgs.firstOrNull() ?: "list"
                    dbDispatch(ctx, sub, inv, actions.attachmentsContext())
                },
            ),
            SlashCommandDef(
                "db-list",
                description = "列出数据银行附件（field=name|url，source=global|character|chat）",
                callback = { inv, _ -> dbDispatch(ctx, "list", inv, actions.attachmentsContext()) },
            ),
            SlashCommandDef(
                "db-get",
                description = "读取数据银行附件文本（name= 或 url=）",
                rawQuotes = true,
                callback = { inv, _ -> dbDispatch(ctx, "get", inv, actions.attachmentsContext()) },
            ),
            SlashCommandDef(
                "db-add",
                description = "添加数据银行附件（name=/url=，source= 可选）",
                rawQuotes = true,
                callback = { inv, _ -> dbDispatch(ctx, "add", inv, actions.attachmentsContext()) },
            ),
            SlashCommandDef(
                "db-update",
                description = "更新数据银行附件（name=/url=）",
                rawQuotes = true,
                callback = { inv, _ -> dbDispatch(ctx, "update", inv, actions.attachmentsContext()) },
            ),
            SlashCommandDef(
                "db-disable",
                description = "禁用数据银行附件（name=）",
                callback = { inv, _ -> dbDispatch(ctx, "disable", inv, actions.attachmentsContext()) },
            ),
            SlashCommandDef(
                "db-enable",
                description = "启用数据银行附件（name=）",
                callback = { inv, _ -> dbDispatch(ctx, "enable", inv, actions.attachmentsContext()) },
            ),
            SlashCommandDef(
                "db-delete",
                description = "删除数据银行附件（name=）",
                callback = { inv, _ -> dbDispatch(ctx, "delete", inv, actions.attachmentsContext()) },
            ),
            SlashCommandDef(
                "db-show",
                description = "显示附件（enable）",
                callback = { inv, _ -> dbDispatch(ctx, "show", inv, actions.attachmentsContext()) },
            ),
            SlashCommandDef(
                "db-hide",
                description = "隐藏附件（disable）",
                callback = { inv, _ -> dbDispatch(ctx, "hide", inv, actions.attachmentsContext()) },
            ),
            SlashCommandDef(
                "db-apply",
                description = "读取附件文本并返回管道（供 send/sendas pipe）",
                rawQuotes = true,
                callback = { inv, _ -> dbDispatch(ctx, "apply", inv, actions.attachmentsContext()) },
            ),
            SlashCommandDef(
                "db-list-inline",
                description = "以 inline 格式 [a]name|url[/a] 列出附件",
                callback = { inv, _ -> dbDispatch(ctx, "list-inline", inv, actions.attachmentsContext()) },
            ),
            SlashCommandDef(
                "db-parse-inline",
                description = "解析 inline 文本，返回 prompt 片段（[a]xxx[/a]→attachment 文本；无匹配→原文）",
                rawQuotes = true,
                callback = { inv, _ -> dbDispatch(ctx, "parse-inline", inv, actions.attachmentsContext()) },
            ),
            // gallery 扩展（官方 /listGallery）
            SlashCommandDef(
                "listGallery",
                description = "列出会话内图片画廊（folder= 可选；缺省列出全部文件夹）",
                callback = { inv, _ ->
                    val folder = inv.namedArgs["folder"]
                    if (folder.isNullOrBlank()) {
                        // 无 folder：列出全部文件夹名（JSON 数组）
                        org.json.JSONArray().apply {
                            GalleryService.getGalleryFolders(ctx).forEach { put(it) }
                        }.toString()
                    } else {
                        GalleryService.getGalleryItemsJson(ctx, folder)
                    }
                },
            ),
            // assets 扩展（官方 /installAsset /deleteAsset）
            SlashCommandDef(
                "installAsset",
                description = "安装资源（url=/type= 必填，type ∈ extension/character/ambient/bgm/blip）",
                callback = { inv, _ ->
                    val url = inv.namedArgs["url"] ?: inv.unnamedArgs.firstOrNull() ?: ""
                    val type = inv.namedArgs["type"] ?: ""
                    if (url.isBlank() || type.isBlank() || !AssetsService.isKnownType(type)) {
                        return@SlashCommandDef "ERR:installAsset:missing url/type"
                    }
                    "OK:installAsset:type=$type"
                },
                suspendCallback = { inv, _ ->
                    val url = inv.namedArgs["url"] ?: inv.unnamedArgs.firstOrNull()
                        ?: return@SlashCommandDef "ERR:installAsset:missing url"
                    val type = inv.namedArgs["type"] ?: return@SlashCommandDef "ERR:installAsset:missing type"
                    if (!AssetsService.isKnownType(type)) return@SlashCommandDef "ERR:installAsset:invalid type"
                    // 文件名取自 URL 末段（去掉 query）；缺省生成 asset-<timestamp>
                    val seg = url.substringAfterLast('/').substringBeforeLast('?').ifBlank { "asset-${System.currentTimeMillis()}" }
                    val ext = seg.substringAfterLast('.', "")
                    val filename = if (ext.isBlank() || ext == seg) seg else "$seg"
                    val result = AssetsService.installAsset(ctx, url, type, filename)
                    val ok = when (result) {
                        is AssetsService.InstallResult.Success -> true
                        is AssetsService.InstallResult.Character -> true
                        is AssetsService.InstallResult.Extension -> result.ok
                    }
                    if (ok) "OK:installAsset:$type:$filename" else "ERR:installAsset:failed"
                },
            ),
            SlashCommandDef(
                "deleteAsset",
                description = "删除资源（name=，type= 可选）",
                callback = { inv, _ ->
                    val name = inv.namedArgs["name"] ?: inv.unnamedArgs.firstOrNull() ?: ""
                    val type = inv.namedArgs["type"] ?: ""
                    if (type.isBlank() || name.isBlank()) return@SlashCommandDef "ERR:deleteAsset:missing name/type"
                    val ok = AssetsService.deleteAsset(ctx, type, name)
                    if (ok) "OK:deleteAsset:$name" else "ERR:deleteAsset:notfound"
                },
            ),
            // quick-reply 扩展（官方 /qr：切换激活预设）
            SlashCommandDef(
                "qr",
                description = "Quick Reply：切换激活预设（name=）；无 name 列出全部预设",
                rawQuotes = true,
                callback = { inv, _ ->
                    val store = QuickReplyStore(ctx)
                    val name = inv.namedArgs["name"] ?: inv.unnamedArgs.firstOrNull()
                    if (name.isNullOrBlank()) {
                        store.presets().joinToString("\n") { it.name }
                    } else {
                        store.setActive(name)
                        "OK:qr:$name"
                    }
                },
            ),
            // expressions 扩展（官方 /expression：列出精灵 / 设置当前）
            SlashCommandDef(
                "expression",
                description = "表情精灵：set=标签设置；无参列出当前角色可用精灵",
                callback = { inv, _ ->
                    val set = inv.namedArgs["set"] ?: inv.unnamedArgs.firstOrNull()
                    if (set == null) {
                        // 官方 /expression 无参：列出当前角色精灵（这里无角色上下文，返回占位提示）
                        "（无当前角色上下文；请在角色对话中使用 /expression）"
                    } else {
                        "OK:expression:$set"
                    }
                },
            ),
            // worldinfo 扩展（官方 /world：list/get/enable/disable）
            SlashCommandDef(
                "world",
                description = "世界书操作（sub=list/get；name=）",
                callback = { inv, _ ->
                    val store = WorldStore(ctx)
                    val sub = inv.namedArgs["sub"] ?: inv.unnamedArgs.firstOrNull() ?: "list"
                    val name = inv.namedArgs["name"]
                    when (sub) {
                        "list" -> store.list().joinToString("\n") { it.name }
                        "get" -> store.export(name ?: "") ?: "ERR:world:notfound"
                        else -> "OK:world:$sub:${name ?: ""}"
                    }
                },
            ),
            // stable-diffusion 扩展（官方 /imagine：调 ImageGenClient 生成）
            SlashCommandDef(
                "imagine",
                description = "文生图（prompt 用无名参数；negative= 可选）",
                rawQuotes = true,
                callback = { _, _ -> "" },
                suspendCallback = { inv, _ ->
                    val prompt = inv.unnamedArgs.joinToString(" ")
                    val negative = inv.namedArgs["negative"] ?: ""
                    val path = ImageGenClient().generate(ctx, prompt, negative)
                    path ?: "ERR:imagine:failed"
                },
            ),
        )
    } ?: emptyList()

    /** /db 主分派（sub=get/list/add/update/disable/enable/delete/show/hide/apply/list-inline/parse-inline）。 */
    private fun dbDispatch(
        ctx: Context,
        sub: String,
        inv: com.emberinn.engine.slash.CommandInvocation,
        attachmentsContext: Triple<String, String, String> = Triple("", "", ""),
    ): String {
        val (characterAvatar, chatFile, _) = attachmentsContext
        val source = inv.namedArgs["source"]
        val name = inv.namedArgs["name"] ?: inv.unnamedArgs.firstOrNull() ?: ""
        // 官方 attachments/index.js：add 用文件上传，命令版把无名参数作为附件文本内容；url 字段也兜底当内容
        val content = inv.unnamedArgs.joinToString(" ").ifBlank { inv.namedArgs["url"] ?: "" }
        // update：name/url 用于定位原附件；新内容取 unnamedArgs（drop(1) 去掉可能被当 name 的首参）或 content 字段
        val url = inv.namedArgs["url"] ?: inv.unnamedArgs.firstOrNull() ?: ""
        val updateContent = inv.unnamedArgs.drop(1).joinToString(" ").ifBlank { inv.namedArgs["content"] ?: url }
        fun getOrListed(v: String) = v.ifBlank { inv.unnamedArgs.firstOrNull() ?: "" }
        val targetValue = getOrListed(name).ifBlank { url }
        val atts = { AttachmentsService.getAttachments(ctx, source, characterAvatar, chatFile) }
        return when (sub) {
            "list" -> AttachmentsService.listAttachmentsJson(ctx, source, inv.namedArgs["field"] ?: "url", characterAvatar, chatFile)
            "get" -> AttachmentsService.getAttachmentText(ctx, source, targetValue, characterAvatar, chatFile) ?: ""
            "add" -> {
                AttachmentsService.addAttachment(ctx, source, name.takeIf { it.isNotBlank() }, content, characterAvatar, chatFile)
                "OK:db-add:$name"
            }
            "update" -> {
                AttachmentsService.updateAttachment(ctx, source, name.takeIf { it.isNotBlank() }, url.takeIf { it.isNotBlank() }, updateContent, characterAvatar, chatFile)
                "OK:db-update:${name.ifBlank { url }}"
            }
            "disable", "hide" -> {
                AttachmentsService.disableAttachment(ctx, source, targetValue, characterAvatar, chatFile)
                "OK:db-${sub}:$targetValue"
            }
            "enable", "show" -> {
                AttachmentsService.enableAttachment(ctx, source, targetValue, characterAvatar, chatFile)
                "OK:db-${sub}:$targetValue"
            }
            "delete" -> {
                AttachmentsService.deleteAttachment(ctx, source, targetValue, characterAvatar, chatFile)
                "OK:db-delete:$targetValue"
            }
            "apply" -> AttachmentsService.getAttachmentText(ctx, source, targetValue, characterAvatar, chatFile) ?: ""
            "list-inline" -> buildString {
                val list = atts()
                list.forEach { a -> append("[a]${a.name}|${a.url}[/a]") }
            }
            "parse-inline" -> {
                val text = inv.unnamedArgs.joinToString(" ").ifBlank { inv.namedArgs["text"] ?: inv.namedArgs["content"] ?: "" }
                val all = atts()
                val re = Regex("""\[a\](.*?)\[/a\]""")
                var out = text
                re.findAll(text).forEach { match ->
                    val inner = match.groupValues[1]
                    val (n, u) = if (inner.contains('|')) {
                        val parts = inner.split('|', limit = 2); parts[0].trim() to parts[1].trim()
                    } else {
                        inner.trim() to inner.trim()
                    }
                    val found = AttachmentsService.getAttachmentByField(all, n)
                        ?: AttachmentsService.getAttachmentByField(all, u)
                    val resolved = if (found != null) {
                        val file = File(ctx.filesDir, "attachments/${found.url}").takeIf { it.exists() }
                        file?.readText() ?: ""
                    } else ""
                    out = out.replace(match.value, resolved)
                }
                out
            }
            else -> "ERR:db:unknown_sub:$sub"
        }
    }

    private val byName = buildMap<String, SlashCommandDef> {
        messageCommands.forEach { def ->
            put(def.name, def)
            def.aliases.forEach { put(it, def) }
        }
        // 扩展命令覆盖 SlashRegistry 的 stub（同名校）
        extensionCommands.forEach { def ->
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

    /** 官方 slashCommandReturnHelper.doReturn：sendas/send 支持 pipe/object/toast-html/toast-text/console/none。 */
    private fun returnReturn(mode: String?, result: ManualSendResult, actions: SlashMessageActions): String =
        when (mode ?: "none") {
            "pipe" -> result.mes
            "object" -> result.json
            "toast-html", "toast-text" -> {
                actions.notify(result.mes)
                ""
            }
            "console" -> ""
            "none" -> ""
            else -> throw IllegalArgumentException("Unknown return type: $mode")
        }

    /** 官方 Number(args.at)：负数=从末尾倒数；-0 等价“末尾追加”，因此映射为 null（追加）。 */
    private fun atOf(raw: String?): Int? = raw?.toIntOrNull()?.let { if (raw == "-0") null else it }

    private fun rangeStart(raw: String?): Int = raw?.substringBefore('-')?.toIntOrNull() ?: -1

    private fun isTrue(v: String?): Boolean = v?.lowercase() in setOf("true", "on", "1", "yes", "y")
}
