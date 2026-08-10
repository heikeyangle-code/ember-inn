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
    /** /sendas：以指定角色插入一条消息（不触发生成）；返回提示文本（空=成功）。 */
    fun sendAsCharacter(name: String, text: String): String
    /** /send：以用户身份插入一条消息（不触发生成）。 */
    fun sendAsUser(text: String): String
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
}

/**
 * App 斜杠执行器：消息类命令（真实动作）+ 引擎纯函数命令。
 * 解析器/闭包/flags 仍是引擎 SlashEngine 1:1，这里只注入命令回调。
 */
class AppSlashExecutor(private val actions: SlashMessageActions) : SlashCommandResolver {

    private val messageCommands = listOf(
        SlashCommandDef(
            "sendas",
            description = "以指定角色发送消息（name= 必填；at= 可插入指定位置）",
            rawQuotes = true,
            callback = { inv, _ ->
                // 官方 sendas：缺省 name 用当前角色名（不报错）；ChatViewModel 兜底
                actions.sendAsCharacter(inv.namedArgs["name"]?.trim().orEmpty(), inv.unnamedArgs.joinToString(" "))
            },
        ),
        SlashCommandDef(
            "send",
            description = "以用户身份发送消息（不触发生成）",
            rawQuotes = true,
            callback = { inv, _ -> actions.sendAsUser(inv.unnamedArgs.joinToString(" ")) },
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
                    atOf(inv.namedArgs["at"]),
                    inv.unnamedArgs.joinToString(" "),
                )
            },
        ),
        SlashCommandDef(
            "message-name",
            description = "获取/设置消息显示名（at= 负数=倒数）",
            callback = { inv, _ ->
                actions.getSetMessageName(
                    atOf(inv.namedArgs["at"]),
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
    )

    private val byName = buildMap<String, SlashCommandDef> {
        messageCommands.forEach { def ->
            put(def.name, def)
            def.aliases.forEach { put(it, def) }
        }
    }

    override fun resolve(name: String): SlashCommandDef? =
        byName[name.lowercase()] ?: SlashRegistry.resolve(name)

    fun execute(text: String, state: SlashState = SlashState()): String =
        SlashEngine.execute(text, state, this)

    private fun atOf(raw: String?): Int = raw?.toIntOrNull() ?: -1

    private fun rangeStart(raw: String?): Int = raw?.substringBefore('-')?.toIntOrNull() ?: -1

    private fun isTrue(v: String?): Boolean = v?.lowercase() in setOf("true", "on", "1", "yes", "y")
}
