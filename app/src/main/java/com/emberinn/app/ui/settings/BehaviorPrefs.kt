package com.emberinn.app.ui.settings

import android.content.Context

/** 官方 power-user 行为设置（对齐 power-user.js 默认值）。 */
data class BehaviorSettings(
    val userPromptBias: String = "",
    val showUserPromptBias: Boolean = true,
    /** 官方 power_user.allow_name2_display（默认关）：显示时保留 AI 消息正文里的“角色名:”前缀，默认剥掉。 */
    val allowName2Display: Boolean = false,
    /** 官方 power_user.allow_name1_display（默认关）：冒充结果保留“用户名:”前缀；
     *  关=冒充收尾剥用户名前缀 + 普通生成以“用户名:”开头的错误名字裁剪（script.js trimNames/trimWrongNames） */
    val allowName1Display: Boolean = false,
    val trimSpaces: Boolean = true,
    val trimSentences: Boolean = false,
    val pinExamples: Boolean = false,
    val stripExamples: Boolean = false,
    val namesAsStopStrings: Boolean = true,
    val messageTokenCount: Boolean = false,
    /** 官方 power_user.auto_scroll_chat_to_bottom（默认开）：新消息自动滚到最新。 */
    val autoScrollChatToBottom: Boolean = true,
    /** 官方 power_user.smooth_streaming 三件套：平滑流式逐字揭示 + 速度(1-100,默认50) + 思考块不平滑。 */
    val smoothStreaming: Boolean = false,
    val smoothStreamingSpeed: Int = 50,
    val smoothStreamingNoThink: Boolean = false,
    /** 官方 power_user.play_message_sound / play_sound_unfocused：回复音效（前台）/仅后台提示。 */
    val playMessageSound: Boolean = false,
    val playSoundUnfocused: Boolean = true,
    val autoSwipe: Boolean = false,
    val autoSwipeMinimumLength: Int = 0,
    val autoSwipeBlacklist: Set<String> = emptySet(),
    val autoSwipeBlacklistThreshold: Int = 2,
    /** 官方 power_user.stream_fade_in（默认关）：流式分词渐显（stream-fadein.js） */
    val streamFadeIn: Boolean = false,
    /** 官方 power_user.gestures（默认开）：消息横滑切变体 */
    val gestures: Boolean = true,
    /** 官方 power_user.send_on_enter：-1 AUTO / 0 关 / 1 开（移动端 AUTO=不发送） */
    val sendOnEnter: Int = 0,
    /** 官方 power_user.quick_continue（默认关）：#mes_continue 快速续写按钮 */
    val quickContinue: Boolean = false,
    /** 官方 power_user.quick_impersonate（默认关）：#mes_impersonate 快速冒充按钮 */
    val quickImpersonate: Boolean = false,
    /** 官方 power_user.auto_save_msg_edits（默认关）：编辑框失焦自动保存 */
    val autoSaveEdits: Boolean = false,
    /** 官方 power_user.chat_truncation（默认 100，滑条 0-1000 step5；0=全部）：
     *  长聊天初始渲染窗口，超窗顶部挂 show more */
    val chatTruncation: Int = 100,
    /** 官方 power_user.streaming_fps（默认 30，滑条 5-100 step5）：流式 tick 更新频率 */
    val streamingFps: Int = 30,
    /** 官方 power_user.spoiler_free_mode（默认关）：角色编辑面板隐藏描述/开场白防剧透（点击 peek） */
    val spoilerFreeMode: Boolean = false,
    /** 官方 power_user.markdown_escape_strings（默认 ''）：非 Markdown 字符串（dinkus 分隔符），
     *  逗号分隔，行首命中则跳过 Markdown 解析（showdown-exclusion 扩展） */
    val markdownEscapeStrings: String = "",
    /** 官方 power_user.sort_field + sort_order（书架排序，power-user.js 默认 name/asc）：
     *  field ∈ name/create_date/fav/date_last_chat/chat_size/data_size；order ∈ asc/desc/random。 */
    val sortField: String = "name",
    val sortOrder: String = "asc",
    /** 官方 power_user.persona_sort_order（默认 asc）：人设列表按显示名 A-Z / Z-A（personas.js sortPersonas）。 */
    val personaSortOrder: String = "asc",
    /** 官方 power_user.fuzzy_search（默认关）：搜索启用模糊匹配（fuse.js 语义近似：
     *  加权字段 + 得分排序），关=普通子串匹配。 */
    val fuzzySearch: Boolean = false,
    /** 官方 power_user.show_tag_filters（官方默认关）：书架顶部显示标签筛选轨道；
     *  本 App 书架以标签轨道为核心浏览方式，故默认开。 */
    val showTagFilters: Boolean = true,
    /** 官方 power_user.aux_field（默认 character_version）：书架角色名下副标题字段，
     *  ∈ character_version / creator；卡内字段为空则不显示（官方同）。 */
    val auxField: String = "character_version",
    /** 官方 power_user.prefer_character_prompt（默认开）：角色卡 system_prompt 覆盖全局系统提示；
     *  关=忽略卡内覆盖，始终用全局（instruct-macros.js/openai.js 同语义）。 */
    val preferCharacterPrompt: Boolean = true,
    /** 官方 power_user.prefer_character_jailbreak（默认开）：角色卡 post_history_instructions 覆盖全局。 */
    val preferCharacterJailbreak: Boolean = true,
    /** 官方 power_user.disable_group_trimming（默认关）：群聊回复不按其他成员名截断清洗。 */
    val disableGroupTrimming: Boolean = false,
    /** 官方 power_user.confirm_message_delete（默认开）：删除消息前弹确认（script.js
     *  .mes_edit_delete → deleteMessage askConfirmation）；关=编辑区删除钮直接删。 */
    val confirmMessageDelete: Boolean = true,
    /** 官方 power_user.restore_user_input（默认开）：重启后恢复输入框未发送草稿
     *  （RossAscends-mods.js restoreUserInput/saveUserInput，localStorage 单键全局口径：
     *  保存不受开关门控，仅恢复受门控）。 */
    val restoreUserInput: Boolean = true,
    /** 官方 power_user.custom_stopping_strings（默认 ''）：JSON 数组文本，生成时并入 API stop
     *  （power-user.js getCustomStoppingStrings：解析→过滤空串→可选宏替换）。 */
    val customStoppingStrings: String = "",
    /** 官方 power_user.custom_stopping_strings_macro（默认开）：停止串先过宏替换。 */
    val customStoppingStringsMacro: Boolean = true,
    /** 官方 power_user.show_group_chat_queue（默认关）：群聊生成时显示本轮回复队列
     *  （group-chats.js printGroupQueue：当前发言成员 + 待生成成员）。 */
    val showGroupChatQueue: Boolean = false,
    /** 官方 power_user.char_list_grid（官方默认 false=列表视图）：书架网格/列表视图切换；
     *  本 App 以海报墙为核心浏览形态，故默认开（网格）。 */
    val charListGrid: Boolean = true,
)

object BehaviorPrefs {

    private const val NAME = "ember_behavior"

    /** 变更总线：save() 时 bump；聊天页按 revision 重读并经 setRuntimeConfig 下发内核 */
    val revision = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** 官方 extension_settings.disabled_attachments：被禁用的附件 URL 列表（attachments 扩展，index.js:168）。 */
    fun disabledAttachments(context: Context): List<String> {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val raw = p.getString("disabled_attachments", "[]") ?: "[]"
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun saveDisabledAttachments(context: Context, urls: List<String>) {
        val arr = org.json.JSONArray()
        urls.forEach { arr.put(it) }
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("disabled_attachments", arr.toString())
            .apply()
    }

    fun load(context: Context): BehaviorSettings {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return BehaviorSettings(
            userPromptBias = p.getString("user_prompt_bias", "") ?: "",
            showUserPromptBias = p.getBoolean("show_user_prompt_bias", true),
            allowName2Display = p.getBoolean("allow_name2_display", false),
            allowName1Display = p.getBoolean("allow_name1_display", false),
            trimSpaces = p.getBoolean("trim_spaces", true),
            trimSentences = p.getBoolean("trim_sentences", false),
            pinExamples = p.getBoolean("pin_examples", false),
            stripExamples = p.getBoolean("strip_examples", false),
            namesAsStopStrings = p.getBoolean("names_as_stop_strings", true),
            messageTokenCount = p.getBoolean("message_token_count_enabled", false),
            autoScrollChatToBottom = p.getBoolean("auto_scroll_chat_to_bottom", true),
            smoothStreaming = p.getBoolean("smooth_streaming", false),
            smoothStreamingSpeed = p.getInt("smooth_streaming_speed", 50),
            smoothStreamingNoThink = p.getBoolean("smooth_streaming_no_think", false),
            playMessageSound = p.getBoolean("play_message_sound", false),
            playSoundUnfocused = p.getBoolean("play_sound_unfocused", true),
            autoSwipe = p.getBoolean("auto_swipe", false),
            autoSwipeMinimumLength = p.getInt("auto_swipe_minimum_length", 0),
            autoSwipeBlacklist = (p.getStringSet("auto_swipe_blacklist", emptySet()) ?: emptySet()).toSet(),
            autoSwipeBlacklistThreshold = p.getInt("auto_swipe_blacklist_threshold", 2),
            streamFadeIn = p.getBoolean("stream_fade_in", false),
            gestures = p.getBoolean("gestures", true),
            sendOnEnter = p.getInt("send_on_enter", 0),
            quickContinue = p.getBoolean("quick_continue", false),
            quickImpersonate = p.getBoolean("quick_impersonate", false),
            autoSaveEdits = p.getBoolean("auto_save_msg_edits", false),
            chatTruncation = p.getInt("chat_truncation", 100).coerceIn(0, 1000),
            streamingFps = p.getInt("streaming_fps", 30).coerceIn(5, 100),
            spoilerFreeMode = p.getBoolean("spoiler_free_mode", false),
            markdownEscapeStrings = p.getString("markdown_escape_strings", "") ?: "",
            sortField = p.getString("sort_field", "name") ?: "name",
            sortOrder = p.getString("sort_order", "asc") ?: "asc",
            personaSortOrder = p.getString("persona_sort_order", "asc") ?: "asc",
            fuzzySearch = p.getBoolean("fuzzy_search", false),
            showTagFilters = p.getBoolean("show_tag_filters", true),
            auxField = p.getString("aux_field", "character_version") ?: "character_version",
            preferCharacterPrompt = p.getBoolean("prefer_character_prompt", true),
            preferCharacterJailbreak = p.getBoolean("prefer_character_jailbreak", true),
            disableGroupTrimming = p.getBoolean("disable_group_trimming", false),
            confirmMessageDelete = p.getBoolean("confirm_message_delete", true),
            restoreUserInput = p.getBoolean("restore_user_input", true),
            customStoppingStrings = p.getString("custom_stopping_strings", "") ?: "",
            customStoppingStringsMacro = p.getBoolean("custom_stopping_strings_macro", true),
            showGroupChatQueue = p.getBoolean("show_group_chat_queue", false),
            charListGrid = p.getBoolean("char_list_grid", true),
        )
    }

    fun save(context: Context, s: BehaviorSettings) {
        com.emberinn.app.data.DisplayCacheVersion.bump()
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("user_prompt_bias", s.userPromptBias)
            .putBoolean("show_user_prompt_bias", s.showUserPromptBias)
            .putBoolean("allow_name2_display", s.allowName2Display)
            .putBoolean("allow_name1_display", s.allowName1Display)
            .putBoolean("trim_spaces", s.trimSpaces)
            .putBoolean("trim_sentences", s.trimSentences)
            .putBoolean("pin_examples", s.pinExamples)
            .putBoolean("strip_examples", s.stripExamples)
            .putBoolean("names_as_stop_strings", s.namesAsStopStrings)
            .putBoolean("message_token_count_enabled", s.messageTokenCount)
            .putBoolean("auto_scroll_chat_to_bottom", s.autoScrollChatToBottom)
            .putBoolean("smooth_streaming", s.smoothStreaming)
            .putInt("smooth_streaming_speed", s.smoothStreamingSpeed)
            .putBoolean("smooth_streaming_no_think", s.smoothStreamingNoThink)
            .putBoolean("play_message_sound", s.playMessageSound)
            .putBoolean("play_sound_unfocused", s.playSoundUnfocused)
            .putBoolean("auto_swipe", s.autoSwipe)
            .putInt("auto_swipe_minimum_length", s.autoSwipeMinimumLength)
            .putStringSet("auto_swipe_blacklist", s.autoSwipeBlacklist)
            .putInt("auto_swipe_blacklist_threshold", s.autoSwipeBlacklistThreshold)
            .putBoolean("stream_fade_in", s.streamFadeIn)
            .putBoolean("gestures", s.gestures)
            .putInt("send_on_enter", s.sendOnEnter)
            .putBoolean("quick_continue", s.quickContinue)
            .putBoolean("quick_impersonate", s.quickImpersonate)
            .putBoolean("auto_save_msg_edits", s.autoSaveEdits)
            .putInt("chat_truncation", s.chatTruncation)
            .putInt("streaming_fps", s.streamingFps)
            .putBoolean("spoiler_free_mode", s.spoilerFreeMode)
            .putString("markdown_escape_strings", s.markdownEscapeStrings)
            .putString("sort_field", s.sortField)
            .putString("sort_order", s.sortOrder)
            .putString("persona_sort_order", s.personaSortOrder)
            .putBoolean("fuzzy_search", s.fuzzySearch)
            .putBoolean("show_tag_filters", s.showTagFilters)
            .putString("aux_field", s.auxField)
            .putBoolean("prefer_character_prompt", s.preferCharacterPrompt)
            .putBoolean("prefer_character_jailbreak", s.preferCharacterJailbreak)
            .putBoolean("disable_group_trimming", s.disableGroupTrimming)
            .putBoolean("confirm_message_delete", s.confirmMessageDelete)
            .putBoolean("restore_user_input", s.restoreUserInput)
            .putString("custom_stopping_strings", s.customStoppingStrings)
            .putBoolean("custom_stopping_strings_macro", s.customStoppingStringsMacro)
            .putBoolean("show_group_chat_queue", s.showGroupChatQueue)
            .putBoolean("char_list_grid", s.charListGrid)
            .apply()
        revision.value += 1
    }

    /** 官方输入草稿（RossAscends-mods.js getUserInputKey：localStorage 单键全局，不分角色/会话）。
     *  独立读写不走 save()：不打断 revision 总线，不触发内核配置重发。 */
    fun loadUserInputDraft(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString("user_input_draft", "") ?: ""

    fun saveUserInputDraft(context: Context, draft: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("user_input_draft", draft)
            .apply()
    }
}
