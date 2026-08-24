package com.emberinn.app.ui.settings


import androidx.compose.ui.unit.sp
import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.ShellActionButton
import com.emberinn.app.ui.design.components.ShellChip
import com.emberinn.app.ui.design.components.EmberSwitch
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.icons.FaIcons
import com.emberinn.engine.regex.RegexPipelineScript

/** 全局正则脚本（对齐官方 regex 扩展 GLOBAL 分桶；聊天发送 USER_INPUT/AI_OUTPUT 位点生效）。 */
@Composable
fun RegexScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var scripts by remember { mutableStateOf(GlobalRegexPrefs.read(context)) }
    var regexEnabled by remember { mutableStateOf(GlobalRegexPrefs.enabled(context)) }
    // 预设正则（官方 preset 扩展 regex_scripts 字段；App 命名预设集模拟）
    var presetSets by remember { mutableStateOf(GlobalRegexPrefs.presetSets(context)) }
    var activePreset by remember { mutableStateOf(GlobalRegexPrefs.activePresetSet(context)) }
    var presetAllowedOpenAI by remember { mutableStateOf(GlobalRegexPrefs.presetAllowed(context, "openai")) }
    var newPresetName by remember { mutableStateOf("") }
    var addingPreset by remember { mutableStateOf(false) }
    var presetEditingIndex by remember { mutableStateOf<Int?>(null) }

    fun savePresetSetsState(next: Map<String, List<RegexPipelineScript>>) {
        GlobalRegexPrefs.savePresetSets(context, next)
        presetSets = next
    }

    fun addPresetSet() {
        val name = newPresetName.trim()
        if (name.isBlank()) return
        val next = if (presetSets.containsKey(name)) presetSets else presetSets + (name to emptyList())
        savePresetSetsState(next)
        activePreset = name
        GlobalRegexPrefs.saveActivePresetSet(context, name)
        newPresetName = ""
    }
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }
    var draftFind by remember { mutableStateOf("") }
    var draftReplace by remember { mutableStateOf("") }
    var draftDisabled by remember { mutableStateOf(false) }
    var draftPlacement by remember { mutableStateOf(listOf(1, 2)) }

    fun persist(next: List<RegexPipelineScript>) {
        GlobalRegexPrefs.save(context, next)
        scripts = next
    }

    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "正则脚本（全局）", onBack = onBack, sky = settingsSky)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            GroupLabel("全局正则")
            Text(
                "对齐官方 regex 扩展 GLOBAL 分桶：先跑全局、再跑该卡正则（该卡在角色详情页编辑）；仅影响发送内容，不落盘改写。",
                color = EmberTheme.colors.inkMute,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用正则脚本", color = EmberTheme.colors.ink, fontSize = 15.sp)
                    Text(
                        "关闭后所有位点不应用正则（官方 disabledExtensions.regex）。",
                        color = EmberTheme.colors.inkMute,
                        fontSize = 12.sp,
                    )
                }
                EmberSwitch(
                    checked = regexEnabled,
                    onChange = { on ->
                        regexEnabled = on
                        GlobalRegexPrefs.saveEnabled(context, on)
                    },
                )
            }
            if (scripts.isEmpty()) {
                Text(
                    "还没有全局正则。可用来统一清理输入输出，例如去掉“*”强调。点下方按钮新增第一条。",
                    color = EmberTheme.colors.inkMute,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                scripts.forEachIndexed { index, script ->
                    RegexRow(
                        name = script.scriptName,
                        find = script.findRegex,
                        enabled = !script.disabled,
                        onClick = {
                            editing = index
                            draftName = script.scriptName
                            draftFind = script.findRegex
                            draftReplace = script.replaceString
                            draftDisabled = script.disabled
                            draftPlacement = script.placement
                        },
                        onDelete = { persist(scripts.filterIndexed { i, _ -> i != index }) },
                        onToggle = { on -> persist(scripts.mapIndexed { i, s -> if (i == index) s.copy(disabled = !on) else s }) },
                    )
                }
            }
            ShellActionButton(
                label = "＋ 新增全局正则",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                adding = true
                draftName = ""
                draftFind = ""
                draftReplace = ""
                draftDisabled = false
                draftPlacement = listOf(1, 2)
            }

            GroupLabel("预设正则")
            Text(
                "对齐官方 preset 扩展：脚本存于预设的 regex_scripts 扩展字段；App 用命名预设集模拟，允许列表按官方 preset_allowed_regex[api]（App 固定 openai）。",
                color = EmberTheme.colors.inkMute,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                ShellInput(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = "新预设集名",
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ShellActionButton(label = "新建") { addPresetSet() }
            }
            if (presetSets.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    presetSets.keys.sorted().forEach { name ->
                        ShellChip(name, selected = activePreset == name) {
                            activePreset = name
                            GlobalRegexPrefs.saveActivePresetSet(context, name)
                        }
                    }
                }
                // 官方 RegexPresetManager.applyPreset + change 事件：显式“应用”按钮把选中预设集设为激活，
                // bump DisplayCacheVersion → ChatScreen 版本监听即时用新预设重算消息显示。
                ShellActionButton(
                    label = "应用此预设集（当前：${activePreset.ifBlank { "（未选择）" }}）",
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    enabled = activePreset.isNotBlank(),
                ) {
                    GlobalRegexPrefs.saveActivePresetSet(context, activePreset)
                    Toast.makeText(context, "已应用预设：$activePreset", Toast.LENGTH_SHORT).show()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("允许此预设集（当前：${activePreset.ifBlank { "（未选择）" }}）", color = EmberTheme.colors.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    EmberSwitch(
                        checked = activePreset.isNotBlank() && activePreset in presetAllowedOpenAI,
                        onChange = { on ->
                            if (activePreset.isBlank()) return@EmberSwitch
                            val next = if (on) (presetAllowedOpenAI + activePreset).distinct() else presetAllowedOpenAI - activePreset
                            presetAllowedOpenAI = next
                            GlobalRegexPrefs.savePresetAllowed(context, "openai", next)
                        },
                    )
                }
                presetSets[activePreset].orEmpty().forEachIndexed { i, script ->
                    RegexRow(
                        name = script.scriptName,
                        find = script.findRegex,
                        enabled = !script.disabled,
                        onClick = {
                            presetEditingIndex = i
                            draftName = script.scriptName
                            draftFind = script.findRegex
                            draftReplace = script.replaceString
                            draftDisabled = script.disabled
                            draftPlacement = script.placement
                        },
                        onDelete = {
                            val list = presetSets[activePreset].orEmpty().filterIndexed { j, _ -> j != i }
                            savePresetSetsState(presetSets + (activePreset to list))
                        },
                        onToggle = { on ->
                            val list = presetSets[activePreset].orEmpty().mapIndexed { j, s -> if (j == i) s.copy(disabled = !on) else s }
                            savePresetSetsState(presetSets + (activePreset to list))
                        },
                    )
                }
                ShellActionButton(
                    label = "＋ 新增预设正则",
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    addingPreset = true
                    adding = false
                    editing = null
                    presetEditingIndex = null
                    draftName = ""
                    draftFind = ""
                    draftReplace = ""
                    draftDisabled = false
                    draftPlacement = listOf(1, 2)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    }

    if (adding || editing != null || presetEditingIndex != null) {
        AlertDialog(
            onDismissRequest = {
                adding = false
                editing = null
                addingPreset = false
                presetEditingIndex = null
            },
            title = {
                Text(
                    when {
                        addingPreset || presetEditingIndex != null ->
                            if (addingPreset) "新增预设正则" else "编辑预设正则"
                        adding -> "新增全局正则"
                        else -> "编辑全局正则"
                    },
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ShellInput(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = "名称",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ShellInput(
                        value = draftFind,
                        onValueChange = { draftFind = it },
                        label = "匹配式（支持 /pat/flags）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    ShellInput(
                        value = draftReplace,
                        onValueChange = { draftReplace = it },
                        label = "替换串（留空=删除匹配）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        "生效位点",
                        color = EmberTheme.colors.inkMute,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShellChip("用户输入", selected = 1 in draftPlacement) {
                            draftPlacement = if (1 in draftPlacement) draftPlacement - 1 else draftPlacement + 1
                        }
                        ShellChip("AI 输出", selected = 2 in draftPlacement) {
                            draftPlacement = if (2 in draftPlacement) draftPlacement - 2 else draftPlacement + 2
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("禁用", color = EmberTheme.colors.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        EmberSwitch(checked = draftDisabled, onChange = { draftDisabled = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draftFind.isNotBlank()) {
                        val script = RegexPipelineScript(
                            scriptName = draftName.trim(),
                            findRegex = draftFind,
                            replaceString = draftReplace,
                            disabled = draftDisabled,
                            placement = draftPlacement.ifEmpty { listOf(1, 2) },
                        )
                        if (addingPreset || presetEditingIndex != null) {
                            val list = presetSets[activePreset].orEmpty().toMutableList()
                            val updated = if (addingPreset) {
                                list + script
                            } else {
                                list.mapIndexed { i, s -> if (i == presetEditingIndex) script else s }
                            }
                            savePresetSetsState(presetSets + (activePreset to updated))
                        } else {
                            val next = if (adding) scripts + script else scripts.mapIndexed { i, s -> if (i == editing) script else s }
                            persist(next)
                        }
                    } else {
                        Toast.makeText(context, "匹配式不能为空", Toast.LENGTH_SHORT).show()
                    }
                    adding = false
                    editing = null
                    addingPreset = false
                    presetEditingIndex = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    adding = false
                    editing = null
                    addingPreset = false
                    presetEditingIndex = null
                }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun RegexRow(
    name: String,
    find: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
    ) {
        Column(modifier = Modifier.weight(1f).clickable(onClick = onClick)) {
            Text(name.ifBlank { "（未命名）" }, color = c.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(find.ifBlank { "（空匹配式）" }, color = c.inkMute, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            FaIcons.Pencil, contentDescription = "编辑", tint = c.inkMute,
            modifier = Modifier.size(17.dp).clickable(onClick = onClick).padding(2.dp),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            FaIcons.TrashCan, contentDescription = "删除", tint = c.danger,
            modifier = Modifier.size(17.dp).clickable(onClick = onDelete).padding(2.dp),
        )
        Spacer(Modifier.width(10.dp))
        EmberSwitch(checked = enabled, onChange = onToggle)
    }
}
