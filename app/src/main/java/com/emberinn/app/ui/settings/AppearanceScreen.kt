package com.emberinn.app.ui.settings


import com.emberinn.app.ui.design.components.ShellSheet
import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.EmberSwitch
import com.emberinn.app.ui.components.EmberToasts
import com.emberinn.app.ui.components.EmberSlider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.emberinn.app.data.FontManager
import com.emberinn.app.data.OfficialThemeManager
import com.emberinn.app.ui.design.AppearanceBus
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.InkTier
import com.emberinn.app.ui.design.components.InkText
import com.emberinn.app.ui.design.components.SectionTitle
import com.emberinn.app.ui.design.components.SurfaceCard
import com.emberinn.app.ui.design.components.EmberChip
import com.emberinn.app.ui.design.components.ChipRow
import com.emberinn.app.ui.design.components.SheetRow
import com.emberinn.app.ui.design.components.SheetRowTone
import com.emberinn.app.ui.icons.FaIcons
import kotlinx.coroutines.launch

/**
 * 外观（DESIGN_SYSTEM §五）：酒馆官方主题管理 + 显示偏好。
 * 官方主题=唯一换装来源：字段推导供内核与壳层（ShellTheme），无独立皮肤体系。
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val official = remember { OfficialThemeManager.shared(context) }
    val themes by official.themes.collectAsState()
    val currentTheme by official.currentName.collectAsState()

    var radius by remember { mutableStateOf(AppearancePrefs.radius(context)) }
    var font by remember { mutableStateOf(AppearancePrefs.font(context)) }
    val fontScope = rememberCoroutineScope()
    var fontDownloading by remember { mutableStateOf(false) }
    var fontError by remember { mutableStateOf<String?>(null) }

    // 官方主题：导入 / 导出 / 管理（导出、另存、删除收进弹层）
    var manageSheetFor by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var saveAsSheet by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText()
                    ?: error("无法读取文件")
                val name = official.import(text)
                EmberToasts.show(context, "已导入：$name")
            }.onFailure { e ->
                EmberToasts.show(context, "导入失败：${e.message}")
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val name = pendingExport
        if (uri != null && name != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(official.export(name)?.toByteArray()) }
                EmberToasts.show(context, "已导出：${name}.json")
            }.onFailure { e ->
                EmberToasts.show(context, "导出失败：${e.message}")
            }
        }
        pendingExport = null
    }

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "外观", onBack = onBack, sky = settingsSky)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ---------------- 官方内容主题（壳层换装唯一来源：字段推导整壳生效） ----------------
                item {
                    Column {
                        SectionTitle("酒馆官方主题")
                        InkText(
                            "内核渲染层配色，与官方 SillyTavern 主题文件完全互导；强调色自动协调到壳层",
                            tier = InkTier.Mute,
                            sizeSp = 12f,
                        )
                    }
                }
                items(themes.size) { index ->
                    val meta = themes[index]
                    val active = meta.name == currentTheme
                    SurfaceCard(modifier = Modifier.fillMaxWidth(), onClick = { official.select(meta.name) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                InkText(meta.name, tier = InkTier.Primary, sizeSp = 14f, fontWeight = FontWeight.Medium)
                                InkText(
                                    if (meta.bundled) "内置" else "导入",
                                    tier = InkTier.Mute,
                                    sizeSp = 11f,
                                )
                            }
                            if (active) InkText("使用中", tier = InkTier.Primary, sizeSp = 12f, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                item {
                    ChipRow {
                        EmberChip(label = "导入主题 JSON", selected = false, onClick = { importLauncher.launch(arrayOf("*/*")) })
                        EmberChip(label = "管理当前主题", selected = false, onClick = { manageSheetFor = currentTheme })
                    }
                }

                // ---------------- 显示 ----------------
                item {
                    SectionTitle("显示")
                    PreferenceGroup {
                        GroupLabel("全局圆角")
                        ChipRow(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                            listOf("default" to "系统", "square" to "方正", "rounded" to "圆润", "circle" to "浑圆").forEach { (v, label) ->
                                EmberChip(
                                    label = label,
                                    selected = radius == v,
                                    onClick = { radius = v; AppearancePrefs.save(context, radius, font); AppearanceBus.notifyChanged() },
                                )
                            }
                        }
                    }
                }
                item {
                    PreferenceGroup {
                        GroupLabel("全局字体")
                        ChipRow(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                            listOf(
                                "default" to "系统",
                                "serif" to "衬线",
                                "noto" to "Noto Sans",
                            ).forEach { (v, label) ->
                                val fontReady = when (v) {
                                    "noto" -> FontManager.notoReady(context)
                                    else -> true
                                }
                                EmberChip(
                                    label = if (v == "noto" && !fontReady) "$label ↓" else label,
                                    selected = font == v,
                                    onClick = {
                                        when {
                                            v == "noto" && !fontReady -> {
                                                font = "noto"
                                                fontScope.launch {
                                                    fontDownloading = true
                                                    val result = FontManager.ensureNoto(context)
                                                    fontDownloading = false
                                                    result.onSuccess {
                                                        AppearancePrefs.save(context, radius, "noto")
                                                        AppearanceBus.notifyChanged()
                                                    }.onFailure { e ->
                                                        font = AppearancePrefs.font(context)
                                                        fontError = e.message ?: "未知错误"
                                                    }
                                                }
                                            }
                                            else -> {
                                                font = v
                                                AppearancePrefs.save(context, radius, v)
                                                AppearanceBus.notifyChanged()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // ---------------- 官方主题字段微调（=官方 User Settings 面板，写回主题 JSON） ----------------
                item {
                    ThemeTuneGroup()
                }
            }
        }
    }

    // 管理当前主题弹层
    val manageTarget = manageSheetFor
    if (manageTarget != null) {
        val meta = themes.firstOrNull { it.name == manageTarget }
        ShellSheet(onDismiss = { manageSheetFor = null }, title = manageTarget) {
            SheetRow(
                label = "导出 JSON",
                icon = FaIcons.Download,
                onClick = {
                    pendingExport = manageTarget
                    exportLauncher.launch("${manageTarget}.json")
                    manageSheetFor = null
                },
            )
            // 官方 saveTheme(name)「另存为新主题」（power-user.js L2484）：当前主题克隆+改名存新文件
            SheetRow(
                label = "另存为新主题",
                icon = FaIcons.Copy,
                onClick = {
                    saveAsSheet = true
                    manageSheetFor = null
                },
            )
            if (meta != null && !meta.bundled) {
                SheetRow(
                    label = "删除主题",
                    icon = FaIcons.TrashCan,
                    tone = SheetRowTone.Danger,
                    onClick = {
                        confirmDelete = manageTarget
                        manageSheetFor = null
                    },
                )
            }
        }
    }

    // 另存为新主题：名称输入（官方 callGenericPopup INPUT 同构；空名/重名即拒）
    if (saveAsSheet) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveAsSheet = false },
            title = { Text("另存为新主题") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("主题名称") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = official.saveAs(newName)
                    EmberToasts.show(context, if (ok) "已另存：${newName.trim()}" else "名称为空或已存在")
                    if (ok) saveAsSheet = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { saveAsSheet = false }) { Text("取消") }
            },
        )
    }

    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除主题") },
            text = { Text("确定删除「$name」？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = official.delete(name)
                    EmberToasts.show(context, if (ok) "已删除" else "删除失败")
                    confirmDelete = null
                }) { Text("删除", color = EmberTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            },
        )
    }

    if (fontDownloading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("下载字体") },
            text = { Text("正在下载字体（Noto Sans 4 面约 2.2MB），完成后自动应用，请稍候…") },
            confirmButton = {},
        )
    }
    fontError?.let { err ->
        AlertDialog(
            onDismissRequest = { fontError = null },
            title = { Text("字体下载失败") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { fontError = null }) { Text("知道了") }
            },
        )
    }
}

/** 设置分组容器：surface 卡统一包裹。 */
@Composable
private fun PreferenceGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = EmberTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EmberTheme.shapes.cornerCard))
            .background(c.surface)
            .padding(14.dp),
        content = content,
    )
}

@Composable
internal fun GroupLabel(text: String) {
    InkText(text, tier = InkTier.Soft, sizeSp = 13f, fontWeight = FontWeight.Medium)
}

@Composable
internal fun SwitchPrefRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EmberTheme.colors.inkMute)
        }
        EmberSwitch(checked = checked, onChange = onToggle)
    }
}

/** 滑条行（官方 User Settings 滑条语义）：拖动本地态、松手才写回主题 JSON。 */
@Composable
private fun SliderPrefRow(
    title: String,
    initial: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    enabled: Boolean = true,
    onCommit: (Float) -> Unit,
) {
    var v by remember(initial) { mutableStateOf(initial) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkText(title, tier = InkTier.Soft, sizeSp = 13f, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            InkText(format(v), tier = InkTier.Mute, sizeSp = 11f)
        }
        Slider(
            value = v,
            onValueChange = { v = it },
            onValueChangeFinished = { onCommit(v) },
            valueRange = range,
            steps = steps,
            enabled = enabled,
        )
    }
}

/**
 * 官方主题字段微调面板（= 官方 User Settings 的主题子集）：
 * 字段/枚举/滑条范围逐项对照 power-user.js getThemeObject 与 index.html 滑条 min/max；
 * 写回当前主题 JSON（OfficialThemeManager.updateFields），内置主题首次修改 copy-on-write。
 * 官方主题 JSON 是唯一数据源——这里不再有第二套外观存储。
 */
@Composable
private fun ThemeTuneGroup() {
    val context = LocalContext.current
    val manager = remember { OfficialThemeManager.shared(context) }
    val themeJson by manager.currentThemeJson.collectAsState()
    val obj = remember(themeJson) {
        runCatching { Json.parseToJsonElement(themeJson ?: "{}").jsonObject }.getOrNull()
    }

    fun boolVal(key: String, def: Boolean): Boolean =
        obj?.get(key)?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: def
    fun numVal(key: String, def: Double): Double =
        obj?.get(key)?.let { (it as? JsonPrimitive)?.doubleOrNull } ?: def
    fun strVal(key: String, def: String): String =
        obj?.get(key)?.let { (it as? JsonPrimitive)?.contentOrNull } ?: def
    fun setField(key: String, value: JsonElement) = manager.updateFields(mapOf(key to value))
    fun setBool(key: String, v: Boolean) = setField(key, JsonPrimitive(v))
    fun setInt(key: String, v: Int) = setField(key, JsonPrimitive(v))

    val fastUi = boolVal("fast_ui_mode", true)
    val noShadows = boolVal("noShadows", false)
    val stylePack by manager.currentStylePack.collectAsState()

    PreferenceGroup {
        GroupLabel("消息布局与头像")
        ChipRow(modifier = Modifier.padding(top = 6.dp)) {
            listOf(0 to "平铺", 1 to "气泡", 2 to "文档").forEach { (v, label) ->
                EmberChip(
                    label = label,
                    selected = numVal("chat_display", 0.0).toInt() == v,
                    onClick = { setInt("chat_display", v) },
                )
            }
        }
        // Moonlit Echoes 扩展布局（chat_display 3..7 → echostyle 等 body 类）：
        // 仅样式包在位时提供入口——CSS 未加载时这些类无样式，选择无意义
        if (stylePack.enabled) {
            Spacer(Modifier.height(8.dp))
            GroupLabel("Moonlit 消息样式（扩展布局）")
            ChipRow(modifier = Modifier.padding(top = 6.dp)) {
                listOf(3 to "Echo", 4 to "Whisper", 5 to "Hush", 6 to "Ripple", 7 to "Tide").forEach { (v, label) ->
                    EmberChip(
                        label = label,
                        selected = numVal("chat_display", 0.0).toInt() == v,
                        onClick = { setInt("chat_display", v) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GroupLabel("头像样式")
        ChipRow(modifier = Modifier.padding(top = 6.dp)) {
            listOf(0 to "圆形", 1 to "大图", 2 to "方形", 3 to "圆角").forEach { (v, label) ->
                EmberChip(
                    label = label,
                    selected = numVal("avatar_style", 0.0).toInt() == v,
                    onClick = { setInt("avatar_style", v) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        GroupLabel("提示弹窗位置（toastr_position）")
        ChipRow(modifier = Modifier.padding(top = 6.dp)) {
            listOf(
                "toast-top-left" to "左上", "toast-top-center" to "上中", "toast-top-right" to "右上",
                "toast-bottom-left" to "左下", "toast-bottom-center" to "下中", "toast-bottom-right" to "右下",
            ).forEach { (v, label) ->
                EmberChip(
                    label = label,
                    selected = strVal("toastr_position", "toast-top-center") == v,
                    onClick = { setField("toastr_position", JsonPrimitive(v)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        GroupLabel("媒体附件展示")
        ChipRow(modifier = Modifier.padding(top = 6.dp)) {
            listOf("list" to "列表", "gallery" to "画廊").forEach { (v, label) ->
                EmberChip(
                    label = label,
                    selected = strVal("media_display", "list") == v,
                    onClick = { setField("media_display", JsonPrimitive(v)) },
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    PreferenceGroup {
        GroupLabel("缩放与尺寸（官方滑条范围）")
        SliderPrefRow(
            title = "字体缩放 font_scale",
            initial = numVal("font_scale", 1.0).toFloat(),
            range = 0.5f..1.5f,
            steps = 99,
            format = { "%.2fx".format(it) },
            onCommit = { setField("font_scale", JsonPrimitive(it.toDouble())) },
        )
        SliderPrefRow(
            title = "模糊强度 blur_strength",
            initial = numVal("blur_strength", 10.0).toFloat(),
            range = 0f..30f,
            steps = 29,
            format = { "${it.toInt()} px" },
            enabled = !fastUi,
            onCommit = { setField("blur_strength", JsonPrimitive(it.toInt())) },
        )
        SliderPrefRow(
            title = "阴影宽度 shadow_width",
            initial = numVal("shadow_width", 2.0).toFloat(),
            range = 0f..5f,
            steps = 4,
            format = { "${it.toInt()} px" },
            enabled = !noShadows,
            onCommit = { setField("shadow_width", JsonPrimitive(it.toInt())) },
        )
        SliderPrefRow(
            title = "聊天宽度 chat_width",
            initial = numVal("chat_width", 50.0).toFloat(),
            range = 25f..100f,
            steps = 74,
            format = { "${it.toInt()}%" },
            onCommit = { setField("chat_width", JsonPrimitive(it.toInt())) },
        )
    }

    Spacer(Modifier.height(10.dp))

    PreferenceGroup {
        GroupLabel("显示开关")
        SwitchPrefRow("快速模式（无模糊）", "fast_ui_mode：开=关掉全部毛玻璃", fastUi) { setBool("fast_ui_mode", it) }
        SwitchPrefRow("无阴影模式", "noShadows", noShadows) { setBool("noShadows", it) }
        SwitchPrefRow("视觉小说模式", "waifuMode：聊天区收到底部，顶部留立绘空间", boolVal("waifuMode", false)) { setBool("waifuMode", it) }
        SwitchPrefRow("减弱动画", "reduced_motion", boolVal("reduced_motion", false)) { setBool("reduced_motion", it) }
        SwitchPrefRow("显示时间戳", "timestamps_enabled", boolVal("timestamps_enabled", true)) { setBool("timestamps_enabled", it) }
        SwitchPrefRow("时间戳旁模型图标", "timestamp_model_icon", boolVal("timestamp_model_icon", false)) { setBool("timestamp_model_icon", it) }
        SwitchPrefRow("显示消息计时器", "timer_enabled", boolVal("timer_enabled", true)) { setBool("timer_enabled", it) }
        SwitchPrefRow("显示 token 计数", "message_token_count_enabled", boolVal("message_token_count_enabled", false)) { setBool("message_token_count_enabled", it) }
        SwitchPrefRow("显示楼层号", "mesIDDisplay_enabled（官方默认关）", boolVal("mesIDDisplay_enabled", false)) { setBool("mesIDDisplay_enabled", it) }
        SwitchPrefRow("隐藏聊天头像", "hideChatAvatars_enabled", boolVal("hideChatAvatars_enabled", false)) { setBool("hideChatAvatars_enabled", it) }
        SwitchPrefRow("展开全部消息按钮", "expand_message_actions", boolVal("expand_message_actions", false)) { setBool("expand_message_actions", it) }
        SwitchPrefRow("所有消息显示滑动箭头", "show_swipe_num_all_messages", boolVal("show_swipe_num_all_messages", false)) { setBool("show_swipe_num_all_messages", it) }
        SwitchPrefRow("紧凑输入区", "compact_input_area", boolVal("compact_input_area", false)) { setBool("compact_input_area", it) }
        SwitchPrefRow("头像热替换", "hotswap_enabled", boolVal("hotswap_enabled", true)) { setBool("hotswap_enabled", it) }
        SwitchPrefRow("Zen 滑条", "enableZenSliders", boolVal("enableZenSliders", false)) { setBool("enableZenSliders", it) }
        SwitchPrefRow("Lab 模式", "enableLabMode", boolVal("enableLabMode", false)) { setBool("enableLabMode", it) }
        SwitchPrefRow("点击正文进入编辑", "click_to_edit", boolVal("click_to_edit", false)) { setBool("click_to_edit", it) }
    }

    Spacer(Modifier.height(10.dp))

    ColorCssGroup(obj)
    StylePackVarGroup()
}

/**
 * 主题包变量微调（Moonlit Echoes preset 26 项等）：当前样式包在位时展示。
 * 值逐键透传内核（键 → --CSS 自定义属性，官方扩展 settings 的 CSS 消费子集）；
 * 布尔值开关、颜色/尺寸/时长等字符串文本输入（rgba(...)/%/px/s 原样生效）。
 * 覆盖层 SharedPreferences 存储（与 preset 合并下发），恢复默认一键清空回 preset。
 */
/** 样式包设置定义（settings-schema.json 单项）：类型化 UI 渲染依据 */
private data class PackSettingDef(
    val varId: String,
    val type: String,
    val displayText: String,
    val defaultStr: String,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val options: List<Pair<String, String>> = emptyList(),
    val category: String = "",
)

/** schema 单项 JSON → 定义（default/布尔/数字统一字符串化，内核 cssBlock 判定同格式） */
private fun parsePackSettingDef(el: JsonObject): PackSettingDef? {
    val varId = (el["varId"] as? JsonPrimitive)?.contentOrNull ?: return null
    val type = (el["type"] as? JsonPrimitive)?.contentOrNull ?: "text"
    fun str(k: String): String? = (el[k] as? JsonPrimitive)?.let { p ->
        when {
            p.isString -> p.content
            p.booleanOrNull != null -> p.booleanOrNull.toString()
            else -> p.contentOrNull
        }
    }
    val options = (el["options"] as? JsonArray)?.mapNotNull { o ->
        val obj = o as? JsonObject ?: return@mapNotNull null
        val label = (obj["label"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val value = str("value") ?: (obj["value"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        label to value
    } ?: emptyList()
    return PackSettingDef(
        varId = varId,
        type = type,
        displayText = (el["displayText"] as? JsonPrimitive)?.contentOrNull ?: varId,
        defaultStr = str("default") ?: "",
        min = (el["min"] as? JsonPrimitive)?.doubleOrNull,
        max = (el["max"] as? JsonPrimitive)?.doubleOrNull,
        step = (el["step"] as? JsonPrimitive)?.doubleOrNull,
        options = options,
        category = (el["category"] as? JsonPrimitive)?.contentOrNull ?: "",
    )
}

@Composable
private fun StylePackVarGroup() {
    val context = LocalContext.current
    val manager = remember(context) { OfficialThemeManager.shared(context) }
    val stylePack by manager.currentStylePack.collectAsState()
    if (!stylePack.enabled) return
    val vars = remember(stylePack.varsJson) {
        runCatching { Json.parseToJsonElement(stylePack.varsJson ?: "{}").jsonObject }.getOrNull()
    } ?: return

    // schema 驱动类型化 UI（官方扩展 theme-settings.json 定义）；无 schema 的包回落键值编辑
    val defs = remember(stylePack.schemaJson) {
        stylePack.schemaJson?.let { sj ->
            runCatching { Json.parseToJsonElement(sj).jsonObject }
                .getOrNull()?.get("settings") as? JsonArray
        }?.mapNotNull { parsePackSettingDef(it as? JsonObject ?: return@mapNotNull null) }
    }

    PreferenceGroup {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            GroupLabel("主题包变量（即时生效）")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { manager.resetStylePackVars() }) { Text("恢复默认") }
        }
        if (defs == null) {
            // 通用保底：无 schema 的包按键值对编辑（值仍透传 CSS 变量）
            vars.forEach { (key, valueEl) ->
                val value = (valueEl as? JsonPrimitive)?.contentOrNull ?: valueEl.toString()
                if (value == "true" || value == "false") {
                    SwitchPrefRow(key, "布尔开关（透传 --$key）", value == "true") {
                        manager.updateStylePackVar(key, if (it) "true" else "false")
                    }
                } else {
                    PackTextField(varId = key, label = key, value = value) { manager.updateStylePackVar(key, it) }
                }
            }
            return@PreferenceGroup
        }
        // 官方 tabMappings 分组顺序 + 中文组名
        val categoryOrder = listOf(
            "theme-colors" to "主题颜色",
            "chat-style" to "全局消息样式",
            "background-effects" to "背景效果",
            "theme-extras" to "主题附加",
            "raw-css" to "自定义 CSS",
            "chat-general" to "聊天通用",
            "visual-novel" to "视觉小说模式",
            "chat-echo" to "Echo 消息样式",
            "chat-whisper" to "Whisper 消息样式",
            "chat-ripple" to "Ripple 消息样式",
            "mobile-global-settings" to "移动端全局",
            "mobile-detailed-settings" to "移动端细节",
        )
        val grouped = defs.groupBy { it.category }
        val ordered = categoryOrder.filter { it.first in grouped } +
            grouped.keys.filter { it !in categoryOrder.map { c -> c.first } }.map { it to it }
        ordered.forEach { (cat, catLabel) ->
            Spacer(Modifier.height(10.dp))
            GroupLabel(catLabel)
            grouped[cat]?.forEach { def ->
                val cur = (vars[def.varId] as? JsonPrimitive)?.let { p ->
                    if (p.isString) p.content else p.toString()
                } ?: def.defaultStr
                when (def.type) {
                    "checkbox" -> SwitchPrefRow(
                        packLabel(def.varId),
                        def.varId,
                        cur == "true",
                    ) { manager.updateStylePackVar(def.varId, if (it) "true" else "false") }
                    "slider" -> PackSliderRow(
                        label = packLabel(def.varId),
                        value = cur.toDoubleOrNull() ?: def.defaultStr.toDoubleOrNull() ?: 0.0,
                        min = def.min ?: 0.0,
                        max = def.max ?: 10.0,
                        step = def.step ?: 1.0,
                    ) { v -> manager.updateStylePackVar(def.varId, v) }
                    "select" -> {
                        Text(
                            packLabel(def.varId),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        )
                        ChipRow(modifier = Modifier.padding(top = 4.dp)) {
                            def.options.forEach { (label, optVal) ->
                                EmberChip(
                                    label = label,
                                    selected = cur == optVal,
                                    onClick = { manager.updateStylePackVar(def.varId, optVal) },
                                )
                            }
                        }
                    }
                    "textarea" -> PackTextField(
                        varId = def.varId,
                        label = packLabel(def.varId),
                        value = cur,
                        singleLine = false,
                    ) { manager.updateStylePackVar(def.varId, it) }
                    "color" -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 16.dp, end = 8.dp)
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(packPreviewColor(cur)),
                        )
                        PackTextField(varId = def.varId, label = packLabel(def.varId), value = cur) {
                            manager.updateStylePackVar(def.varId, it)
                        }
                    }
                    else -> PackTextField(
                        varId = def.varId,
                        label = packLabel(def.varId),
                        value = cur,
                    ) { manager.updateStylePackVar(def.varId, it) }
                }
            }
        }
    }
}

/** 单行/多行文本微调：改后出现「应用」按钮（颜色 rgba()/尺寸 px/%/时长 s 原样透传） */
@Composable
private fun PackTextField(varId: String, label: String, value: String, singleLine: Boolean = true, onApply: (String) -> Unit) {
    var draft by remember(varId, value) { mutableStateOf(value) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        trailingIcon = {
            if (draft != value) {
                TextButton(onClick = { onApply(draft) }) { Text("应用") }
            }
        },
    )
}

/** 滑条微调：数值格式化（整数不带小数点，与官方 default 字面量一致） */
@Composable
private fun PackSliderRow(label: String, value: Double, min: Double, max: Double, step: Double, onApply: (String) -> Unit) {
    var draft by remember(label, value) { mutableStateOf(value.coerceIn(min, max)) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(
                if (step % 1.0 == 0.0) draft.toInt().toString() else draft.toString(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Slider(
            value = draft.toFloat(),
            onValueChange = { draft = it.toDouble() },
            onValueChangeFinished = {
                val v = if (step % 1.0 == 0.0) draft.toInt().toString() else draft.toString()
                onApply(v)
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = if (step <= 0) 0 else ((max - min) / step).toInt().coerceAtLeast(1) - 1,
        )
    }
}

/** 颜色预览解析：rgba(...) / #hex → Compose Color；失败回落边框色 */
private fun packPreviewColor(spec: String): androidx.compose.ui.graphics.Color = runCatching {
    val s = spec.trim()
    if (s.startsWith("#")) {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(s))
    } else {
        val m = Regex("rgba?\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)\\s*,\\s*([\\d.]+)\\s*(?:,\\s*([\\d.]+)\\s*)?\\)").find(s)
        if (m != null) {
            val (r, g, b, a) = m.destructured
            androidx.compose.ui.graphics.Color(
                (r.toFloat() / 255f).coerceIn(0f, 1f),
                (g.toFloat() / 255f).coerceIn(0f, 1f),
                (b.toFloat() / 255f).coerceIn(0f, 1f),
                (a.toFloatOrNull() ?: 1f).coerceIn(0f, 1f),
            )
        } else {
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(s))
        }
    }
}.getOrDefault(androidx.compose.ui.graphics.Color.Gray)

/** 官方英文标签 → 中文（Moonlit theme-settings 59 项；未知回落英文原文） */
private fun packLabel(varId: String): String = when (varId) {
    "customThemeColor" -> "主主题色"
    "customThemeColor2" -> "次主题色"
    "customBgColor1" -> "主背景色"
    "customBgColor2" -> "次背景色"
    "customTopBarColor" -> "顶栏颜色"
    "Drawer-iconColor" -> "菜单图标颜色"
    "sheldBackgroundColor" -> "聊天区背景色"
    "customScrollbarColor" -> "滚动条颜色"
    "hideAvatarBorder" -> "隐藏头像边框"
    "custom-ChatAvatar" -> "聊天区头像尺寸"
    "mesParagraphSpacingTop" -> "段落上间距"
    "mesParagraphSpacingBottom" -> "段落下间距"
    "charNameFontSize" -> "角色名字号"
    "userNameFontSize" -> "用户名字号"
    "messageTextFontSize" -> "正文字号"
    "messageLineHeight" -> "正文行高"
    "messageTextLetterSpacing" -> "正文字距"
    "customlastInContext" -> "上下文末尾标记样式"
    "customCSS-bg-blur" -> "背景模糊强度"
    "customCSS-bg-opacity" -> "背景图不透明度"
    "sheldBlurStrength" -> "聊天区模糊强度"
    "mobileSheldBlurStrength" -> "移动端聊天区模糊强度"
    "enableThemeColorization" -> "主题色应用到更多 UI"
    "disableTopMenuAnimation" -> "禁用顶栏菜单动画"
    "forceFixedMenuHeight" -> "锁定 AI 回复/角色菜单高度"
    "newMenuMaxHeight" -> "动态调整菜单最大高度"
    "disableAllBorderRadius" -> "禁用所有圆角"
    "expandEntryInputWidth" -> "扩展输入框宽度"
    "compactWorldsLorebooksTopBar" -> "世界书紧凑顶栏"
    "rawCustomCss" -> "原生自定义 CSS"
    "customCSS-ChatGradientBlur" -> "聊天区渐变模糊"
    "showLLMReasoningIcon" -> "推理块显示 LLM 图标"
    "justifyParagraphText" -> "段落两端对齐"
    "enableMessageDetails" -> "隐藏附加消息详情"
    "messageDetailsAnimationDuration" -> "消息详情动画时长"
    "favoriteSymbol" -> "收藏符号"
    "favoriteSymbolAnimation" -> "收藏符号动画"
    "VN-sheld-height" -> "VN 模式聊天区高度"
    "VN-expression-holder" -> "VN 模式立绘渐变透明"
    "custom-EchoAvatarWidth" -> "[Echo] 背景头像宽"
    "custom-EchoAvatarHeight" -> "[Echo] 背景头像高"
    "custom-EchoAvatarMobileWidth" -> "[Echo] 移动端背景头像宽"
    "custom-EchoAvatarMobileHeight" -> "[Echo] 移动端背景头像高"
    "hideEchoUserIllustration" -> "[Echo] 隐藏用户消息插图"
    "hideMobileEchoBackground" -> "[Echo] 移动端隐藏消息背景"
    "customWhisperAvatarWidth" -> "[Whisper] 背景头像宽"
    "customWhisperAvatarAlign" -> "[Whisper] 头像对齐"
    "customRippleAvatarWidth" -> "[Ripple] 头像宽"
    "customRippleAvatarMobileWidth" -> "[Ripple] 移动端头像宽"
    "hideRippleUserAvatar" -> "[Ripple] 隐藏用户头像"
    "enableMobile-hidden_scrollbar" -> "移动端隐藏滚动条"
    "enableMobile-send_form" -> "移动端新输入框样式"
    "inlineMobileMeta" -> "移动端内联名字/时间/图标"
    "increaseMobileInputSpacing" -> "移动端输入区间距加大"
    "increaseDesktopInputSpacing" -> "桌面端输入区间距加大"
    "fixTabletMenuLayout" -> "平板菜单布局修正"
    "mobileQRsBarHeight" -> "移动端快捷回复栏高度"
    "moveQRsBelowInputMobile" -> "移动端快捷回复栏下移"
    "enableMobile-horizontal_hotswap" -> "移动端横向角色卡滚动"
    else -> varId
}

/** 颜色十项 + 自定义 CSS：官方主题颜色类字段的调节入口（hex 文本，点应用写回主题）。 */
@Composable
private fun ColorCssGroup(obj: JsonObject?) {
    val context = LocalContext.current
    val manager = remember(context) { OfficialThemeManager.shared(context) }
    val colorFields = listOf(
        "main_text_color" to "主文字",
        "italics_text_color" to "斜体文字",
        "underline_text_color" to "下划线文字",
        "quote_text_color" to "引用文字",
        "blur_tint_color" to "模糊色调",
        "chat_tint_color" to "聊天色调",
        "user_mes_blur_tint_color" to "用户消息底色",
        "bot_mes_blur_tint_color" to "AI 消息底色",
        "shadow_color" to "阴影色",
        "border_color" to "边框色",
    )
    var drafts by remember(obj) {
        mutableStateOf(colorFields.associate { (k, _) ->
            k to (obj?.get(k)?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "")
        })
    }
    var cssDraft by remember(obj) {
        mutableStateOf(obj?.get("custom_css")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "")
    }

    PreferenceGroup {
        GroupLabel("颜色（官方主题色板，hex/rgba）")
        colorFields.forEach { (key, label) ->
            OutlinedTextField(
                value = drafts[key] ?: "",
                onValueChange = { drafts = drafts + (key to it) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        GroupLabel("自定义 CSS（custom_css）")
        OutlinedTextField(
            value = cssDraft,
            onValueChange = { cssDraft = it },
            textStyle = MaterialTheme.typography.bodySmall,
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            TextButton(onClick = {
                val updates = buildMap<String, JsonElement> {
                    colorFields.forEach { (k, _) ->
                        val v = drafts[k]?.trim().orEmpty()
                        if (v.isNotEmpty()) put(k, JsonPrimitive(v))
                    }
                    if (cssDraft.isNotBlank()) put("custom_css", JsonPrimitive(cssDraft))
                }
                if (updates.isNotEmpty()) manager.updateFields(updates)
            }) { Text("应用到当前主题") }
        }
    }
}
