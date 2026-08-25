package com.emberinn.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        val blurStrength: Int = 10,        // 官方默认 10（power-user.js L156），滑条 0-30
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
        /** 长聊天截断条数（官方 power_user.chat_truncation 默认 100，滑条 0-1000 step5；0=全部） */
        val chatTruncation: Int = 100,
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
        /** 设置定义 schema（官方扩展 theme-settings.js 提取）：类型化 UI 驱动 + 默认值打底。
         *  通用机制——包目录带 settings-schema.json 即生效（内置/导入同规则），不带则 UI 回落键值编辑 */
        val schemaJson: String? = null,
        /** checkbox 型设置启用时注入的内嵌 CSS（官方 index.js cssBlock 语义）：varId → css 文本 */
        val cssBlocksJson: String? = null,
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

    /** 导入官方主题 JSON 字符串；重名覆盖。返回主题名。
     *  亦接受官方预设 JSON（{moonlitEchoesPreset, presetName, settings}）：落为用户预设包
     *  （官方 preset-manager handleMoonlitPresetImport 语义——导入即成同名预设，
     *  选中对应主题自动生效；同官方，预设名即主题名）。 */
    fun import(jsonText: String): String {
        val obj = json.parseToJsonElement(jsonText).let { it as? JsonObject }
            ?: error("不是有效的主题 JSON 对象")
        if (isOfficialPreset(obj)) {
            val presetName = presetNameOf(obj) ?: error("预设缺少 presetName")
            File(themesDir, sanitize(presetName) + PRESET_SUFFIX).writeText(jsonText)
            // 当前主题与预设同名则立即生效（官方导入后 applyPresetToSettings + 激活）
            _currentStylePack.value = detectStylePack()
            return presetName
        }
        // 官方主题至少含一个颜色字段或 name；宽松校验，与官方导入一致
        val name = themeName(obj, "imported.json")
        val fileName = sanitize(name) + ".json"
        File(themesDir, fileName).writeText(jsonText)
        reload()
        return name
    }

    /** 官方预设 JSON 判定（preset-manager handleMoonlitPresetImport 三要素校验） */
    private fun isOfficialPreset(obj: JsonObject): Boolean {
        val marker = obj["moonlitEchoesPreset"] as? JsonPrimitive ?: return false
        return (marker.booleanOrNull == true || marker.contentOrNull == "true") &&
            presetNameOf(obj) != null && obj["settings"] is JsonObject
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

    /** 样式包探测：主题 JSON 同目录有无 style.css / extension.css / settings-schema.json
     *  （内置走 AssetManager，导入走 File；schema 与 cssBlocks 探测同规则——通用不硬编码）。
     *  预设按 presetName == 主题名 匹配（官方 preset-manager/theme-selector：预设名即主题名，
     *  未命中回落 schema 全默认——官方 default-settings 的 "Moonlit Echoes" 默认预设同构）。
     *  导入主题本地无包时，若与内置样式包主题同名则借用该包（官方"扩展全局常驻"的包级等价：
     *  ST 导出的 Moonlit 变体主题 JSON 导入后仍带全套调整选项）。 */
    private fun detectStylePack(): StylePack {
        val loc = locators[_currentName.value] ?: return StylePack()
        val themeName = _currentName.value
        return if (loc.bundled) {
            val dir = loc.path.substringBeforeLast('/')
            val listing = runCatching { context.assets.list(dir)?.toSet() }.getOrNull() ?: emptySet()
            if (STYLE_PACK_FILE !in listing) StylePack()
            else bundledPack(dir, themeName, listing)
        } else {
            val dir = File(loc.path).parentFile ?: return StylePack()
            if (File(dir, STYLE_PACK_FILE).exists()) {
                val schemaFile = File(dir, SCHEMA_FILE)
                val schema = if (schemaFile.exists()) runCatching { schemaFile.readText() }.getOrNull() else null
                StylePack(
                    enabled = true,
                    href = "$STYLE_PACK_HREF_IMPORTED$STYLE_PACK_FILE",
                    extensionHref = File(dir, EXTENSION_CSS_FILE).takeIf { it.exists() }
                        ?.let { "$STYLE_PACK_HREF_IMPORTED$EXTENSION_CSS_FILE" },
                    varsJson = mergePackVarOverrides(presetVarsFromDir(dir, themeName), schema),
                    schemaJson = schema,
                    cssBlocksJson = cssBlocksFromSchema(schema),
                )
            } else {
                val packDir = bundledPackDirFor(themeName) ?: return StylePack()
                bundledPack(packDir, themeName)
            }
        }
    }

    /** 内置样式包组装（dir = assets 相对目录） */
    private fun bundledPack(dir: String, themeName: String, listing: Set<String>? = null): StylePack {
        val files = listing ?: runCatching { context.assets.list(dir)?.toSet() }.getOrNull() ?: emptySet()
        val schema = if (SCHEMA_FILE in files) runCatching {
            context.assets.open("$dir/$SCHEMA_FILE").bufferedReader().readText()
        }.getOrNull() else null
        return StylePack(
            enabled = true,
            // 内置包必须走 /assets/ 前缀——WebViewAssetLoader 只注册了该 handler，
            // 此前 "/themes/..." 直连 404 → style.css 从未加载 → 应用 Moonlit 无任何视觉变化
            href = "/assets/$dir/$STYLE_PACK_FILE",
            extensionHref = if (EXTENSION_CSS_FILE in files) "/assets/$dir/$EXTENSION_CSS_FILE" else null,
            // 预设查找序：用户导入（filesDir，官方"预设存用户设置区"语义，同官方导入覆盖）
            // → 包内预设 → schema 全默认
            varsJson = mergePackVarOverrides(
                presetVarsFromDir(themesDir, themeName) ?: presetVarsFromAssets(dir, themeName),
                schema,
            ),
            schemaJson = schema,
            cssBlocksJson = cssBlocksFromSchema(schema),
        )
    }

    /** 内置样式包主题名 → 包目录：扫描含 style.css 的 assets/themes/<dir>/ 内全部主题 JSON */
    private fun bundledPackDirFor(themeName: String): String? {
        val dirs = runCatching { context.assets.list(THEME_ASSETS_ROOT) }.getOrNull() ?: return null
        for (dir in dirs) {
            val path = "$THEME_ASSETS_ROOT/$dir"
            val listing = runCatching { context.assets.list(path) }.getOrNull() ?: continue
            if (STYLE_PACK_FILE !in listing) continue
            for (f in listing.filter { it.endsWith(".json") }) {
                val obj = parseOrNull(
                    runCatching {
                        context.assets.open("$path/$f").bufferedReader().readText()
                    }.getOrNull() ?: continue
                ) ?: continue
                if (looksLikeTheme(obj) && themeName(obj, f) == themeName) return path
            }
        }
        return null
    }

    /** schema 里带 cssBlock 的项提取为 varId → css 文本（官方 index.js updateCheckboxStyles 数据源） */
    private fun cssBlocksFromSchema(schemaJson: String?): String? {
        if (schemaJson == null) return null
        val schema = runCatching { json.parseToJsonElement(schemaJson) }.getOrNull() as? JsonObject ?: return null
        val settings = schema["settings"] as? kotlinx.serialization.json.JsonArray ?: return null
        val blocks = mutableMapOf<String, String>()
        settings.forEach { el ->
            val obj = el as? JsonObject ?: return@forEach
            val varId = (obj["varId"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
            val cssBlock = (obj["cssBlock"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
            blocks[varId] = cssBlock
        }
        if (blocks.isEmpty()) return null
        return JsonObject(blocks.mapValues { JsonPrimitive(it.value) }).toString()
    }

    /**
     * 主题包变量微调（Moonlit preset 26 项等）：覆盖层存 SharedPreferences，
     * 三态合并后作为 varsJson 下发（官方 settings-service 语义同构）：
     *   schema 默认值（打底） ← preset 值（预设快照） ← 用户覆盖（微调面板）
     * 改即重算 currentStylePack → ChatScreen collectAsState → 内核 applyStylePack 广播，即时生效。
     */
    private val packVarPrefs get() = context.getSharedPreferences("style_pack_vars", Context.MODE_PRIVATE)

    fun updateStylePackVar(key: String, value: String) {
        packVarPrefs.edit().putString(key, value).apply()
        _currentStylePack.value = detectStylePack()
    }

    fun resetStylePackVars() {
        packVarPrefs.edit().clear().apply()
        _currentStylePack.value = detectStylePack()
    }

    private fun mergePackVarOverrides(presetVars: String?, schemaJson: String?): String? {
        val merged = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        // 1) schema 默认值打底（官方 settings-service 初始化：全部定义项都有默认值）
        if (schemaJson != null) {
            val schema = runCatching { json.parseToJsonElement(schemaJson) }.getOrNull() as? JsonObject
            (schema?.get("settings") as? kotlinx.serialization.json.JsonArray)?.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                val varId = (obj["varId"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                val default = obj["default"] ?: return@forEach
                merged[varId] = default
            }
        }
        // 2) preset 快照覆盖（预设只存显式值，其余保持 schema 默认）
        (presetVars?.let { parseOrNull(it) as? JsonObject })?.let { merged.putAll(it) }
        // 3) 用户微调覆盖
        packVarPrefs.all.forEach { (k, v) -> merged[k] = JsonPrimitive(v.toString()) }
        if (merged.isEmpty()) return presetVars
        return JsonObject(merged).toString()
    }

    /** 内置预设包 vars：presetName == 主题名 的 *-preset.json（官方预设名即主题名）
     *  → settings 原样序列化；未命中返回 null（回落 schema 全默认）。 */
    private fun presetVarsFromAssets(dir: String, themeName: String): String? {
        val names = runCatching { context.assets.list(dir) }.getOrNull() ?: return null
        for (f in names.filter { it.endsWith(PRESET_SUFFIX) }) {
            val obj = parseOrNull(runCatching {
                context.assets.open("$dir/$f").bufferedReader().readText()
            }.getOrNull() ?: continue) ?: continue
            if (presetNameOf(obj) != themeName) continue
            (obj["settings"] as? JsonObject)?.let { return it.toString() }
        }
        return null
    }

    private fun presetVarsFromDir(dir: File, themeName: String): String? {
        val names = dir.listFiles { f -> f.name.endsWith(PRESET_SUFFIX) } ?: return null
        for (f in names) {
            val obj = parseOrNull(runCatching { f.readText() }.getOrNull() ?: continue) ?: continue
            if (presetNameOf(obj) != themeName) continue
            (obj["settings"] as? JsonObject)?.let { return it.toString() }
        }
        return null
    }

    /** 官方预设 JSON：{moonlitEchoesPreset, presetVersion, presetName, settings} */
    private fun presetNameOf(obj: JsonObject): String? =
        obj["presetName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

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
            blurStrength = num("blur_strength")?.coerceIn(0, 30) ?: 10,
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
            chatTruncation = num("chat_truncation")?.toInt()?.coerceIn(0, 1000) ?: 100,
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

    /**
     * 官方 saveTheme(name)「另存为新主题」（power-user.js L2484-2516）：
     * 当前主题 JSON 克隆 + name 换新名 → filesDir/themes/ 新文件 → 自动切换选中。
     * 空名/重名返回 false（官方 importTheme "Theme with that name already exists" 语义）。
     */
    fun saveAs(newName: String): Boolean {
        val name = newName.trim()
        if (name.isEmpty() || locators.containsKey(name)) return false
        val raw = _currentThemeJson.value ?: return false
        val obj = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return false
        val renamed = JsonObject(obj.toMutableMap().apply { put("name", JsonPrimitive(name)) })
        File(themesDir, sanitize(name) + ".json").writeText(renamed.toString())
        reload()
        select(name, persist = true)
        return true
    }

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
        /** 设置定义 schema 约定文件名：样式包目录带此文件即启用类型化设置 UI（通用探测） */
        private const val SCHEMA_FILE = "settings-schema.json"

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
