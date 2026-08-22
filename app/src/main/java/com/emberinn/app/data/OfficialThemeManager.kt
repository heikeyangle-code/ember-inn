package com.emberinn.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 官方主题管理器：与 SillyTavern 主题文件 100% 互导（docs/DESIGN_SYSTEM.md §二点六）。
 *
 * 职责：
 *  - 内置主题（官方 5 套 + Moonlit 2 套）从 assets 加载
 *  - 用户导入：任意官方格式主题 JSON（34 字段），无损保存
 *  - 当前主题：原始 JSON 透传给内核 applyTheme（全字段，不丢开关）
 *  - 壳层设置派生：chatDisplay/avatarStyle/compactInputArea 等供原生 UI 读取
 *
 * 不做的事：不重写官方字段语义；未知字段原样保留（官方未来加字段零成本兼容）。
 */
class OfficialThemeManager(private val context: Context) {

    data class ThemeMeta(
        val name: String,
        val fileName: String,
        val bundled: Boolean,
    )

    /** 壳层可读的主题派生设置（与 power-user.js 语义一致） */
    data class ShellSettings(
        val chatDisplay: Int = 0,          // 0 平铺 / 1 气泡 / 2 文档
        val avatarStyle: Int = 0,          // 0 默认 / 1 大矩形 / 2 方形
        val compactInputArea: Boolean = false,
        val fastUiMode: Boolean = false,   // no-blur
        val noShadows: Boolean = false,
        val waifuMode: Boolean = false,
        val reducedMotion: Boolean = false,
        val timestampsEnabled: Boolean = true,
        val timerEnabled: Boolean = true,
        val messageTokenCountEnabled: Boolean = false,
        val mesIdDisplayEnabled: Boolean = true,
        val hideChatAvatars: Boolean = false,
        val expandMessageActions: Boolean = false,
        val showSwipeNumAllMessages: Boolean = false,
        val enableZenSliders: Boolean = false,
        val hotswapEnabled: Boolean = true,
        val bogusFolders: Boolean = false,
        val enableLabMode: Boolean = false,
        val zoomedAvatarMagnification: Boolean = false,
        val fontScale: Double = 1.0,
        val chatWidth: Double = 50.0,
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val themesDir: File get() = File(context.filesDir, "themes").apply { mkdirs() }

    private val _themes = MutableStateFlow<List<ThemeMeta>>(emptyList())
    val themes: StateFlow<List<ThemeMeta>> = _themes

    private val _currentThemeJson = MutableStateFlow<String?>(null)
    /** 当前主题原始 JSON（直接喂内核 window.Kernel.applyTheme） */
    val currentThemeJson: StateFlow<String?> = _currentThemeJson

    private val _currentName = MutableStateFlow(DEFAULT_THEME)
    val currentName: StateFlow<String> = _currentName

    private val prefs get() = context.getSharedPreferences("official_theme", Context.MODE_PRIVATE)

    init {
        reload()
        // 恢复上次选择；无记录则用内置默认（Moonlit Glimmer）
        val saved = prefs.getString(KEY_CURRENT, DEFAULT_THEME) ?: DEFAULT_THEME
        select(saved, persist = false)
    }

    fun reload() {
        val list = mutableListOf<ThemeMeta>()
        // 内置：assets/themes/<dir>/*.json
        runCatching {
            val root = "themes"
            context.assets.list(root)?.forEach { dir ->
                context.assets.list("$root/$dir")?.filter { it.endsWith(".json") }?.forEach { f ->
                    val obj = parseOrNull(context.assets.open("$root/$dir/$f").bufferedReader().readText())
                    if (obj != null) list += ThemeMeta(themeName(obj, f), f, bundled = true)
                }
            }
        }
        // 用户导入：filesDir/themes/*.json
        themesDir.listFiles { f -> f.extension == "json" }?.forEach { f ->
            parseOrNull(f.readText())?.let { obj -> list += ThemeMeta(themeName(obj, f.name), f.name, bundled = false) }
        }
        _themes.value = list
    }

    /** 导入官方主题 JSON 字符串；重名覆盖。返回主题名。 */
    fun import(jsonText: String): String {
        val obj = json.parseToJsonElement(jsonText).let { it as? JsonObject }
            ?: error("不是有效的主题 JSON 对象")
        // 官方主题至少含一个颜色字段或 name；宽松校验，与官方导入一致
        val name = themeName(obj, "imported.json")
        val fileName = sanitize(name) + ".json"
        File(themesDir, fileName).writeText(jsonText)
        reload()
        return name
    }

    fun select(name: String, persist: Boolean = true) {
        val raw = readRaw(name)
        if (raw == null) {
            // 回退默认内置
            val fallback = readRaw(DEFAULT_THEME) ?: return
            _currentThemeJson.value = fallback
            _currentName.value = DEFAULT_THEME
        } else {
            _currentThemeJson.value = raw
            _currentName.value = name
        }
        if (persist) prefs.edit().putString(KEY_CURRENT, _currentName.value).apply()
    }

    /** 壳层派生设置（每次取当前主题实时计算） */
    fun shellSettings(): ShellSettings {
        val raw = _currentThemeJson.value ?: return ShellSettings()
        val obj = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return ShellSettings()
        fun num(k: String) = obj[k]?.jsonPrimitive?.intOrNull
        fun bool(k: String) = obj[k]?.jsonPrimitive?.booleanOrNull
        return ShellSettings(
            chatDisplay = num("chat_display") ?: 0,
            avatarStyle = num("avatar_style") ?: 0,
            compactInputArea = bool("compact_input_area") ?: false,
            fastUiMode = bool("fast_ui_mode") ?: false,
            noShadows = bool("noShadows") ?: false,
            waifuMode = bool("waifuMode") ?: false,
            reducedMotion = bool("reduced_motion") ?: false,
            timestampsEnabled = bool("timestamps_enabled") ?: true,
            timerEnabled = bool("timer_enabled") ?: true,
            messageTokenCountEnabled = bool("message_token_count_enabled") ?: false,
            mesIdDisplayEnabled = bool("mesIDDisplay_enabled") ?: true,
            hideChatAvatars = bool("hideChatAvatars_enabled") ?: false,
            expandMessageActions = bool("expand_message_actions") ?: false,
            showSwipeNumAllMessages = bool("show_swipe_num_all_messages") ?: false,
            enableZenSliders = bool("enableZenSliders") ?: false,
            hotswapEnabled = bool("hotswap_enabled") ?: true,
            bogusFolders = bool("bogus_folders") ?: false,
            enableLabMode = bool("enableLabMode") ?: false,
            zoomedAvatarMagnification = bool("zoomed_avatar_magnification") ?: false,
            fontScale = obj["font_scale"]?.jsonPrimitive?.doubleOrNull ?: 1.0,
            chatWidth = obj["chat_width"]?.jsonPrimitive?.doubleOrNull ?: 50.0,
        )
    }

    fun delete(name: String): Boolean {
        if (name == DEFAULT_THEME) return false
        val f = File(themesDir, sanitize(name) + ".json")
        val ok = f.exists() && f.delete()
        if (ok) reload()
        return ok
    }

    fun export(name: String): String? = readRaw(name)

    // ------------------------------------------------------------------

    private fun readRaw(name: String): String? {
        // 先用户目录，后 assets
        val user = File(themesDir, sanitize(name) + ".json")
        if (user.exists()) return user.readText()
        runCatching {
            context.assets.list("themes")?.forEach { dir ->
                context.assets.list("themes/$dir")?.forEach { f ->
                    if (themeNameOf(f) == name) {
                        return context.assets.open("themes/$dir/$f").bufferedReader().readText()
                    }
                }
            }
        }
        return null
    }

    private fun parseOrNull(text: String): JsonObject? = runCatching {
        json.parseToJsonElement(text) as? JsonObject
    }.getOrNull()

    private fun themeName(obj: JsonObject, fallback: String): String =
        obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: obj["presetName"]?.jsonPrimitive?.contentOrNull
            ?: fallback.removeSuffix(".json")

    private fun themeNameOf(fileName: String): String = fileName.removeSuffix(".json")

    private fun sanitize(name: String) = name.replace(Regex("[/\\\\]"), "_")

    companion object {
        const val DEFAULT_THEME = "Glimmer"
        private const val KEY_CURRENT = "current"
    }
}
