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

    /** 壳层可读的主题派生设置（与 power-user.js 语义一致；缺字段回落官方默认值） */
    data class ShellSettings(
        val chatDisplay: Int = 0,          // 0 平铺 / 1 气泡 / 2 文档（3..7 Moonlit 扩展）
        val avatarStyle: Int = 0,          // 0 圆 / 1 大矩形 / 2 方形 / 3 圆角
        val compactInputArea: Boolean = false,
        val fastUiMode: Boolean = true,    // 官方默认 true：no-blur 快速模式
        val noShadows: Boolean = false,
        val waifuMode: Boolean = false,
        val reducedMotion: Boolean = false,
        val timestampsEnabled: Boolean = true,
        val timerEnabled: Boolean = true,
        val messageTokenCountEnabled: Boolean = false,
        val mesIdDisplayEnabled: Boolean = false,   // 官方默认关
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
        /** toast-top-left/center/right × top/bottom 六枚举，官方默认 toast-top-center */
        val toastrPosition: String = "toast-top-center",
        val clickToEdit: Boolean = false,           // 点击消息正文进编辑
        /** 媒体展示全局默认：list / gallery（MEDIA_DISPLAY 枚举） */
        val mediaDisplay: String = "list",
    ) {
        /** 官方 toastr_position → Android Toast 重力（官方 toastr 六位置语义，缺省 top-center） */
        val toastrGravity: Int
            get() = when (toastrPosition) {
                "toast-top-left" -> android.view.Gravity.TOP or android.view.Gravity.START
                "toast-top-right" -> android.view.Gravity.TOP or android.view.Gravity.END
                "toast-bottom-left" -> android.view.Gravity.BOTTOM or android.view.Gravity.START
                "toast-bottom-right" -> android.view.Gravity.BOTTOM or android.view.Gravity.END
                "toast-bottom-center" -> android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                else -> android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            }
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val themesDir: File get() = File(context.filesDir, "themes").apply { mkdirs() }

    /** 主题定位：JSON 展示名 / 文件名 都能索引到同一份原始 JSON（内置走 assets，导入走 filesDir）。 */
    private class Locator(val path: String, val bundled: Boolean, val meta: ThemeMeta)

    private val locators = mutableMapOf<String, Locator>()

    private val _themes = MutableStateFlow<List<ThemeMeta>>(emptyList())
    val themes: StateFlow<List<ThemeMeta>> = _themes

    private val _currentThemeJson = MutableStateFlow<String?>(null)
    /** 当前主题原始 JSON（直接喂内核 window.Kernel.applyTheme） */
    val currentThemeJson: StateFlow<String?> = _currentThemeJson

    private val _currentName = MutableStateFlow(DEFAULT_THEME)
    val currentName: StateFlow<String> = _currentName

    /**
     * 样式包（第三方主题整包 CSS，如 Moonlit style.css）：内核 applyStylePack 直透。
     * 探测规则通用化——当前主题 JSON 同目录存在 style.css 即启用，否则纯官方行为零污染。
     * extensionHref 可选：同目录 extension.css（上游扩展兼容层）存在即一并注入。
     * vars 取同目录 *-preset.json 的 settings 对象（键=CSS 自定义属性名，逐键透传不解释），
     * 无预设包则 null。href 按载体动态解析：内置 → /assets/themes/<dir>/…；
     * 导入 → /themefiles/…（KernelWebViewFactory THEME_FILES_PREFIX 站内源）。
     */
    data class StylePack(
        val enabled: Boolean = false,
        val href: String? = null,
        val extensionHref: String? = null,
        val varsJson: String? = null,
    )

    private val _currentStylePack = MutableStateFlow(StylePack())
    val currentStylePack: StateFlow<StylePack> = _currentStylePack

    private val prefs get() = context.getSharedPreferences("official_theme", Context.MODE_PRIVATE)

    init {
        reload()
        // 恢复上次选择；无记录则用内置默认（Moonlit Glimmer）
        val saved = prefs.getString(KEY_CURRENT, DEFAULT_THEME) ?: DEFAULT_THEME
        select(saved, persist = false)
    }

    fun reload() {
        val list = mutableListOf<ThemeMeta>()
        locators.clear()
        fun put(meta: ThemeMeta, loc: Locator) {
            if (list.none { it.name == meta.name }) list += meta
            locators[meta.name] = loc
            locators[meta.fileName.removeSuffix(".json")] = loc
        }
        // 内置：assets/themes/<dir>/*.json（只收真主题 JSON；同目录的预设包等被 looksLikeTheme 挡掉）
        runCatching {
            context.assets.list(THEME_ASSETS_ROOT)?.forEach { dir ->
                context.assets.list("$THEME_ASSETS_ROOT/$dir")?.filter { it.endsWith(".json") }?.forEach { f ->
                    val path = "$THEME_ASSETS_ROOT/$dir/$f"
                    val obj = parseOrNull(context.assets.open(path).bufferedReader().readText()) ?: return@forEach
                    if (!looksLikeTheme(obj)) return@forEach
                    put(ThemeMeta(themeName(obj, f), f, bundled = true), Locator(path, bundled = true, meta = ThemeMeta(themeName(obj, f), f, bundled = true)))
                }
            }
        }
        // 用户导入：filesDir/themes/*.json
        themesDir.listFiles { f -> f.extension == "json" }?.forEach { f ->
            val obj = parseOrNull(f.readText()) ?: return@forEach
            if (!looksLikeTheme(obj)) return@forEach
            put(ThemeMeta(themeName(obj, f.name), f.name, bundled = false), Locator(f.absolutePath, bundled = false, meta = ThemeMeta(themeName(obj, f.name), f.name, bundled = false)))
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
            _currentName.value = locators[DEFAULT_THEME]?.meta?.name ?: DEFAULT_THEME
        } else {
            _currentThemeJson.value = raw
            // 归一为列表展示名（按文件名命中时，currentName 仍与主题列表一致）
            _currentName.value = (locators[name] ?: locators[DEFAULT_THEME])?.meta?.name ?: name
        }
        _currentStylePack.value = detectStylePack()
        if (persist) prefs.edit().putString(KEY_CURRENT, _currentName.value).apply()
    }

    /** 样式包探测：主题 JSON 同目录有无 style.css / extension.css（内置走 AssetManager，导入走 File） */
    private fun detectStylePack(): StylePack {
        val loc = locators[_currentName.value] ?: return StylePack()
        return if (loc.bundled) {
            val dir = loc.path.substringBeforeLast('/')
            val listing = runCatching { context.assets.list(dir)?.toSet() }.getOrNull() ?: emptySet()
            if (STYLE_PACK_FILE !in listing) StylePack()
            else StylePack(
                enabled = true,
                href = "/$dir/$STYLE_PACK_FILE",
                extensionHref = if (EXTENSION_CSS_FILE in listing) "/$dir/$EXTENSION_CSS_FILE" else null,
                varsJson = presetVarsFromAssets(dir),
            )
        } else {
            val dir = File(loc.path).parentFile ?: return StylePack()
            val css = File(dir, STYLE_PACK_FILE)
            if (!css.exists()) StylePack()
            else StylePack(
                enabled = true,
                href = "$STYLE_PACK_HREF_IMPORTED$STYLE_PACK_FILE",
                extensionHref = File(dir, EXTENSION_CSS_FILE).takeIf { it.exists() }
                    ?.let { "$STYLE_PACK_HREF_IMPORTED$EXTENSION_CSS_FILE" },
                varsJson = presetVarsFromDir(dir),
            )
        }
    }

    /** 内置预设包 vars：目录内首个含 settings 对象的 *-preset.json → 原样序列化 */
    private fun presetVarsFromAssets(dir: String): String? {
        val names = runCatching { context.assets.list(dir) }.getOrNull() ?: return null
        for (f in names.filter { it.endsWith(PRESET_SUFFIX) }) {
            val obj = parseOrNull(runCatching {
                context.assets.open("$dir/$f").bufferedReader().readText()
            }.getOrNull() ?: continue) ?: continue
            (obj["settings"] as? JsonObject)?.let { return it.toString() }
        }
        return null
    }

    private fun presetVarsFromDir(dir: File): String? {
        val names = dir.listFiles { f -> f.name.endsWith(PRESET_SUFFIX) } ?: return null
        for (f in names) {
            val obj = parseOrNull(runCatching { f.readText() }.getOrNull() ?: continue) ?: continue
            (obj["settings"] as? JsonObject)?.let { return it.toString() }
        }
        return null
    }

    /** 壳层派生设置（每次取当前主题实时计算） */
    fun shellSettings(): ShellSettings {
        val raw = _currentThemeJson.value ?: return ShellSettings()
        val obj = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return ShellSettings()
        fun num(k: String) = obj[k]?.jsonPrimitive?.intOrNull
        fun bool(k: String) = obj[k]?.jsonPrimitive?.booleanOrNull
        fun str(k: String) = obj[k]?.jsonPrimitive?.contentOrNull
        return ShellSettings(
            chatDisplay = num("chat_display") ?: 0,
            avatarStyle = num("avatar_style") ?: 0,
            compactInputArea = bool("compact_input_area") ?: false,
            fastUiMode = bool("fast_ui_mode") ?: true,
            noShadows = bool("noShadows") ?: false,
            waifuMode = bool("waifuMode") ?: false,
            reducedMotion = bool("reduced_motion") ?: false,
            timestampsEnabled = bool("timestamps_enabled") ?: true,
            timerEnabled = bool("timer_enabled") ?: true,
            messageTokenCountEnabled = bool("message_token_count_enabled") ?: false,
            mesIdDisplayEnabled = bool("mesIDDisplay_enabled") ?: false,
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
            toastrPosition = str("toastr_position")?.takeIf {
                it in TOASTR_POSITIONS
            } ?: "toast-top-center",
            clickToEdit = bool("click_to_edit") ?: false,
            mediaDisplay = str("media_display")?.takeIf { it == "gallery" || it == "list" } ?: "list",
        )
    }

    fun delete(name: String): Boolean {
        val loc = locators[name] ?: return false
        if (loc.bundled) return false
        val ok = File(loc.path).delete()
        if (ok) reload()
        return ok
    }

    /**
     * 官方主题字段写回（外观页「主题微调」= 官方 User Settings 面板语义）：
     * 合并进当前主题 JSON 并持久化，流自动重发 → 内核 applyTheme 即时生效。
     * 内置主题是只读资源——首次修改 copy-on-write 落到 filesDir/themes/（同名覆盖定位），
     * 与官方"主题即文件、改面板即存文件"行为一致。
     */
    fun updateFields(values: Map<String, kotlinx.serialization.json.JsonElement>) {
        if (values.isEmpty()) return
        val raw = _currentThemeJson.value ?: return
        val obj = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return
        val merged = JsonObject(obj.toMutableMap().apply { putAll(values) })
        val loc = locators[_currentName.value] ?: return
        if (loc.bundled) {
            File(themesDir, sanitize(loc.meta.fileName)).writeText(merged.toString())
            reload() // filesDir 同名条目后注册，locator 覆盖为用户副本
            select(_currentName.value, persist = true)
        } else {
            File(loc.path).writeText(merged.toString())
            _currentThemeJson.value = merged.toString()
        }
        _currentStylePack.value = detectStylePack()
    }

    fun export(name: String): String? = readRaw(name)

    // ------------------------------------------------------------------

    private fun readRaw(name: String): String? {
        val loc = locators[name] ?: return null
        return if (loc.bundled) {
            runCatching { context.assets.open(loc.path).bufferedReader().readText() }.getOrNull()
        } else {
            File(loc.path).takeIf { it.exists() }?.readText()
        }
    }

    /** 官方主题 JSON 识别：至少含一个官方主题字段（挡掉同目录的采样预设包等非主题 JSON）。 */
    private fun looksLikeTheme(obj: JsonObject): Boolean = THEME_FIELD_PROBES.any { obj.containsKey(it) }

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
        private const val THEME_ASSETS_ROOT = "themes"
        private const val STYLE_PACK_FILE = "style.css"
        private const val EXTENSION_CSS_FILE = "extension.css"
        private const val PRESET_SUFFIX = "-preset.json"

        /** 导入主题包的内核站内源（KernelWebViewFactory.THEME_FILES_PREFIX，避免 data→renderer 依赖） */
        private const val STYLE_PACK_HREF_IMPORTED = "/themefiles/"

        /** 官方 power-user.js 主题字段抽样：命中任一即视为主题 JSON（对照官方源码 L162-165）。 */
        private val THEME_FIELD_PROBES = setOf(
            "main_text_color", "blur_tint_color", "chat_tint_color", "quote_text_color",
            "italics_text_color", "border_color", "user_mes_blur_tint_color",
            "bot_mes_blur_tint_color", "custom_css", "chat_display",
        )

        /** 官方 toastr 六位置枚举（power-user.js toastPositionClasses） */
        val TOASTR_POSITIONS = setOf(
            "toast-top-left", "toast-top-center", "toast-top-right",
            "toast-bottom-left", "toast-bottom-center", "toast-bottom-right",
        )

        /** 进程级共享实例：壳层（根主题桥）、聊天页、外观页共用同一份状态流，切主题即时全局生效。 */
        @Volatile private var sharedManager: OfficialThemeManager? = null
        fun shared(context: Context): OfficialThemeManager =
            sharedManager ?: synchronized(this) {
                sharedManager ?: OfficialThemeManager(context.applicationContext).also { sharedManager = it }
            }
    }
}
