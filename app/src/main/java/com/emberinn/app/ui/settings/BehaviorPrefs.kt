package com.emberinn.app.ui.settings

import android.content.Context

/** 官方 power-user 行为设置（对齐 power-user.js 默认值）。 */
data class BehaviorSettings(
    val userPromptBias: String = "",
    val showUserPromptBias: Boolean = true,
    /** 官方 power_user.allow_name2_display（默认关）：显示时保留 AI 消息正文里的“角色名:”前缀，默认剥掉。 */
    val allowName2Display: Boolean = false,
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
        )
    }

    fun save(context: Context, s: BehaviorSettings) {
        com.emberinn.app.data.DisplayCacheVersion.bump()
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("user_prompt_bias", s.userPromptBias)
            .putBoolean("show_user_prompt_bias", s.showUserPromptBias)
            .putBoolean("allow_name2_display", s.allowName2Display)
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
            .apply()
        revision.value += 1
    }
}
