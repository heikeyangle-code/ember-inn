package com.emberinn.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 扩展管理器（SillyTavern extensions.js 机制同构）：
 *
 *  - 发现：assets/extensions/<id>/manifest.json（内置）+ filesDir/extensions/<id>/manifest.json（用户安装）
 *  - manifest 字段与官方一致：display_name / loading_order / js / css / author / version
 *    （kernel 节为本宿主扩展字段：extension_css / settings_schema）
 *  - 启停：disabledExtensions 禁用列表（官方语义：禁用即不注入，重载生效；
 *    本宿主 CSS/变量型即时生效，js 型需重建内核实例——与官方 reload 同构）
 *  - 删除：内置扩展只可禁用（官方 system 扩展同语义），用户安装的可删
 *  - 注入：css → <link>（内核 applyStylePack 通道）；js → <script type="module">（内核通道）
 *
 * 与 [OfficialThemeManager] 单向依赖：预设名即主题名（官方 preset-manager），
 * 主题切换驱动变量重算；主题管理器不感知扩展。
 */
class ExtensionManager private constructor(
    private val context: Context,
    private val themes: OfficialThemeManager,
) {

    /** 扩展清单项（官方 Extensions 列表行） */
    data class ExtensionMeta(
        val id: String,
        val displayName: String,
        val version: String,
        val author: String,
        val bundled: Boolean,
        val enabled: Boolean,
    )

    /**
     * 样式包（manifest css/kernel 节组装）：内核 applyStylePack 直透。
     * href 按载体解析：内置 → /assets/extensions/<id>/…；用户装 → /extfiles/…。
     * vars 三态合并（官方 settings-service 语义）：schema 默认 ← preset（预设名==当前主题名）
     * ← 用户微调（SharedPreferences 覆盖层）。
     */
    data class StylePack(
        val id: String? = null,
        val enabled: Boolean = false,
        val href: String? = null,
        val extensionHref: String? = null,
        val varsJson: String? = null,
        val schemaJson: String? = null,
        val cssBlocksJson: String? = null,
        val jsUrl: String? = null,
    )

    data class State(
        val extensions: List<ExtensionMeta> = emptyList(),
        val pack: StylePack = StylePack(),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** 用户安装扩展根目录（站内源 /extfiles/ 对应 filesDir/extensions） */
    private val extDir: File get() = File(context.filesDir, "extensions").apply { mkdirs() }

    /** manifest.json 解析缓存：id → manifest（发现时填充） */
    private val manifests = mutableMapOf<String, JsonObject>()

    private val prefs get() = context.getSharedPreferences("extensions", Context.MODE_PRIVATE)

    /** 样式包变量微调覆盖层（沿用旧 key，老用户微调不丢） */
    private val packVarPrefs get() = context.getSharedPreferences("style_pack_vars", Context.MODE_PRIVATE)

    init {
        reload()
        // 预设名即主题名：主题切换 → 变量三态重算（官方 theme-selector 联动）
        scope.launch {
            themes.currentName.collect { recompute() }
        }
    }

    fun reload() {
        manifests.clear()
        val list = mutableListOf<ExtensionMeta>()
        val disabled = disabledIds()
        // 内置（官方 system 扩展）：assets/extensions/*/manifest.json
        runCatching {
            context.assets.list(EXT_ASSETS_ROOT)?.forEach { id ->
                readBundledManifest(id)?.let { m ->
                    manifests[id] = m
                    list += metaOf(id, m, bundled = true, disabled)
                }
            }
        }
        // 用户安装（官方 third-party 扩展）：filesDir/extensions/*/manifest.json
        extDir.listFiles { f -> f.isDirectory }?.forEach { dir ->
            val m = runCatching {
                File(dir, MANIFEST_FILE).takeIf { it.exists() }?.let { parseObj(it.readText()) }
            }.getOrNull() ?: return@forEach
            manifests[dir.name] = m
            list += metaOf(dir.name, m, bundled = false, disabled)
        }
        // 官方 loading_order 升序激活（sortManifestsByOrder）
        list.sortBy { (manifests[it.id]?.get("loading_order") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0 }
        _state.value = _state.value.copy(extensions = list)
        recompute()
    }

    /** 启停（官方 disabledExtensions 列表写删） */
    fun setEnabled(id: String, enabled: Boolean) {
        val cur = disabledIds().toMutableSet()
        if (enabled) cur.remove(id) else cur += id
        prefs.edit().putStringSet(KEY_DISABLED, cur).apply()
        reload()
    }

    /** 删除（仅用户安装的；内置只可禁用——官方 system 扩展同语义） */
    fun delete(id: String): Boolean {
        if (isBundled(id)) return false
        val dir = File(extDir, id)
        if (!dir.exists()) return false
        dir.deleteRecursively()
        reload()
        return true
    }

    /**
     * 官方预设 JSON 导入（{moonlitEchoesPreset, presetName, settings}）：
     * 落为启用中样式包扩展的用户预设——官方 preset-manager handleMoonlitPresetImport
     * 语义（导入即成同名预设，选中对应主题自动生效）。返回 presetName，非预设格式返回 null。
     */
    fun tryImportPreset(jsonText: String): String? {
        val obj = parseObj(jsonText) ?: return null
        if (!isOfficialPreset(obj)) return null
        val packId = activePackId() ?: return null
        val presetName = presetNameOf(obj) ?: return null
        File(File(extDir, packId), sanitize(presetName) + PRESET_SUFFIX).writeText(jsonText)
        recompute()
        return presetName
    }

    /** 官方预设格式探测（导入分流用）：{moonlitEchoesPreset, presetName, settings} 三要素齐备 */
    fun isPresetJson(jsonText: String): Boolean =
        parseObj(jsonText)?.let { isOfficialPreset(it) } == true

    fun updateStylePackVar(key: String, value: String) {
        packVarPrefs.edit().putString(key, value).apply()
        recompute()
    }

    fun resetStylePackVars() {
        packVarPrefs.edit().clear().apply()
        recompute()
    }

    // ------------------------------------------------------------------
    // 发现与组装

    private fun readBundledManifest(id: String): JsonObject? = runCatching {
        val text = context.assets.open("$EXT_ASSETS_ROOT/$id/$MANIFEST_FILE").bufferedReader().readText()
        parseObj(text)
    }.getOrNull()

    private fun metaOf(id: String, manifest: JsonObject, bundled: Boolean, disabled: Set<String>) = ExtensionMeta(
        id = id,
        displayName = (manifest["display_name"] as? JsonPrimitive)?.contentOrNull ?: id,
        version = (manifest["version"] as? JsonPrimitive)?.contentOrNull ?: "",
        author = (manifest["author"] as? JsonPrimitive)?.contentOrNull ?: "",
        bundled = bundled,
        enabled = id !in disabled,
    )

    private fun disabledIds(): Set<String> = prefs.getStringSet(KEY_DISABLED, emptySet()) ?: emptySet()

    /** 当前激活样式包所属扩展 id（启用中、带 css 的第一个——官方激活顺序） */
    private fun activePackId(): String? =
        _state.value.extensions.firstOrNull { it.enabled && manifestCss(it.id) != null }?.id

    private fun manifestCss(id: String): String? =
        (manifests[id]?.get("css") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /**
     * 重算激活样式包：扩展启用即全局生效（官方「装了扩展就常驻」语义），
     * 与当前主题无关；变量值随当前主题名匹配预设。
     */
    private fun recompute() {
        val packId = activePackId()
        if (packId == null) {
            _state.value = _state.value.copy(pack = StylePack())
            return
        }
        val m = manifests[packId] ?: run {
            _state.value = _state.value.copy(pack = StylePack())
            return
        }
        val kernel = m["kernel"] as? JsonObject
        val extCss = (kernel?.get("extension_css") as? JsonPrimitive)?.contentOrNull
        val schemaFile = (kernel?.get("settings_schema") as? JsonPrimitive)?.contentOrNull

        val schema: String?
        val cssBlocks: String?
        val presetVars: String?
        if (manifests[packId] != null && isBundled(packId)) {
            schema = schemaFile?.let { f -> runCatching { context.assets.open("$EXT_ASSETS_ROOT/$packId/$f").bufferedReader().readText() }.getOrNull() }
            cssBlocks = cssBlocksFromSchema(schema)
            presetVars = presetVarsFor(packId, themes.currentName.value, bundled = true)
        } else {
            val dir = File(extDir, packId)
            schema = schemaFile?.let { f -> File(dir, f).takeIf { it.exists() }?.readText() }
            cssBlocks = cssBlocksFromSchema(schema)
            presetVars = presetVarsFor(packId, themes.currentName.value, bundled = false)
        }

        val js = (m["js"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val css = manifestCss(packId)!!
        _state.value = _state.value.copy(
            pack = StylePack(
                id = packId,
                enabled = true,
                href = if (isBundled(packId)) "/assets/$EXT_ASSETS_ROOT/$packId/$css" else "$EXT_FILES_PREFIX$packId/$css",
                extensionHref = extCss?.let {
                    if (isBundled(packId)) "/assets/$EXT_ASSETS_ROOT/$packId/$it" else "$EXT_FILES_PREFIX$packId/$it"
                },
                varsJson = mergePackVarOverrides(presetVars, schema),
                schemaJson = schema,
                cssBlocksJson = cssBlocks,
                jsUrl = js?.let { if (isBundled(packId)) "/assets/$EXT_ASSETS_ROOT/$packId/$it" else "$EXT_FILES_PREFIX$packId/$it" },
            ),
        )
    }

    private fun isBundled(id: String): Boolean =
        _state.value.extensions.firstOrNull { it.id == id }?.bundled
            ?: runCatching { context.assets.list("$EXT_ASSETS_ROOT/$id") != null }.getOrDefault(false)

    /** schema 里带 cssBlock 的项提取为 varId → css 文本（官方 index.js updateCheckboxStyles 数据源） */
    private fun cssBlocksFromSchema(schemaJson: String?): String? {
        if (schemaJson == null) return null
        val schema = runCatching { json.parseToJsonElement(schemaJson) }.getOrNull() as? JsonObject ?: return null
        val settings = schema["settings"] as? JsonArray ?: return null
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
     * 变量三态合并（官方 settings-service 语义）：
     * schema 默认值（打底）← preset 值（预设快照）← 用户覆盖（微调面板）
     */
    private fun mergePackVarOverrides(presetVars: String?, schemaJson: String?): String? {
        val merged = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        if (schemaJson != null) {
            val schema = runCatching { json.parseToJsonElement(schemaJson) }.getOrNull() as? JsonObject
            (schema?.get("settings") as? JsonArray)?.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                val varId = (obj["varId"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                val default = obj["default"] ?: return@forEach
                merged[varId] = default
            }
        }
        (presetVars?.let { parseObj(it) })?.let { merged.putAll(it) }
        packVarPrefs.all.forEach { (k, v) -> merged[k] = JsonPrimitive(v.toString()) }
        if (merged.isEmpty()) return presetVars
        return JsonObject(merged).toString()
    }

    /** 预设查找（presetName == 主题名，官方预设名即主题名）：
     *  用户扩展目录 → 内置包目录 → 旧版 themesDir（兼容历史导入） */
    private fun presetVarsFor(packId: String, themeName: String, bundled: Boolean): String? {
        if (bundled) {
            presetVarsFromBundledDir(packId, themeName)?.let { return it }
        } else {
            presetVarsFromDir(File(extDir, packId), themeName)?.let { return it }
        }
        // 旧版 OfficialThemeManager 导入的预设落在 filesDir/themes/
        presetVarsFromDir(File(context.filesDir, "themes"), themeName)?.let { return it }
        return null
    }

    private fun presetVarsFromBundledDir(packId: String, themeName: String): String? {
        val names = runCatching { context.assets.list("$EXT_ASSETS_ROOT/$packId") }.getOrNull() ?: return null
        for (f in names.filter { it.endsWith(PRESET_SUFFIX) }) {
            val obj = parseOrNullAsset("$EXT_ASSETS_ROOT/$packId/$f") ?: continue
            if (presetNameOf(obj) != themeName) continue
            (obj["settings"] as? JsonObject)?.let { return it.toString() }
        }
        return null
    }

    private fun presetVarsFromDir(dir: File, themeName: String): String? {
        val names = dir.listFiles { f -> f.name.endsWith(PRESET_SUFFIX) } ?: return null
        for (f in names) {
            val obj = runCatching { parseObj(f.readText()) }.getOrNull() ?: continue
            if (presetNameOf(obj) != themeName) continue
            (obj["settings"] as? JsonObject)?.let { return it.toString() }
        }
        return null
    }

    /** 官方预设 JSON：{moonlitEchoesPreset, presetVersion, presetName, settings} 三要素校验 */
    private fun isOfficialPreset(obj: JsonObject): Boolean {
        val marker = obj["moonlitEchoesPreset"] as? JsonPrimitive ?: return false
        return (marker.booleanOrNull == true || marker.contentOrNull == "true") &&
            presetNameOf(obj) != null && obj["settings"] is JsonObject
    }

    private fun presetNameOf(obj: JsonObject): String? =
        obj["presetName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun parseOrNullAsset(path: String): JsonObject? = runCatching {
        parseObj(context.assets.open(path).bufferedReader().readText())
    }.getOrNull()

    private fun parseObj(text: String): JsonObject? = runCatching {
        json.parseToJsonElement(text) as? JsonObject
    }.getOrNull()

    private fun sanitize(name: String) = name.replace(Regex("[/\\\\]"), "_")

    companion object {
        private const val EXT_ASSETS_ROOT = "extensions"
        private const val MANIFEST_FILE = "manifest.json"
        private const val PRESET_SUFFIX = "-preset.json"
        private const val KEY_DISABLED = "disabled"

        /** 用户安装扩展站内源（KernelWebViewFactory.EXT_FILES_PREFIX，避免 data→renderer 依赖） */
        private const val EXT_FILES_PREFIX = "/extfiles/"

        /** 进程级共享实例：聊天页/外观页/壳层共用同一份状态流，启停即时全局生效 */
        @Volatile private var shared: ExtensionManager? = null
        fun shared(context: Context): ExtensionManager =
            shared ?: synchronized(this) {
                shared ?: ExtensionManager(
                    context.applicationContext,
                    OfficialThemeManager.shared(context.applicationContext),
                ).also { shared = it }
            }
    }
}
