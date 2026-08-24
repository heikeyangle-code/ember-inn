package com.emberinn.app.ui.settings


import com.emberinn.app.ui.design.components.EmptyState
import com.emberinn.app.ui.components.EmberPrimaryButton
import com.emberinn.app.ui.design.components.EmberSwitch
import com.emberinn.app.ui.components.EmberSecondaryButton
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.emberinn.app.data.QuickReplyStore
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.icons.FaIcons
import com.emberinn.engine.slash.QuickReplySlot

/**
 * 全局快捷回复（对齐官方 Quick Reply 扩展：预设 + 槽位 mes/label/enabled/automationId/preventAutoExecute）。
 * 官方存储 preset 文件；本页读写 filesDir/quick-replies.json（QuickReplyPreset），执行走 QuickReplyExecutor。
 */
@Composable
fun QuickRepliesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { QuickReplyStore(context) }
    var presetName by remember { mutableStateOf(store.activeName().ifBlank { store.presets().firstOrNull()?.name ?: "default" }) }
    var slots by remember { mutableStateOf(store.load(presetName).slots) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var draftPresetName by remember { mutableStateOf("") }
    var presetMenuExpanded by remember { mutableStateOf(false) }
    var draftLabel by remember { mutableStateOf("") }
    var draftMes by remember { mutableStateOf("") }
    var draftEnabled by remember { mutableStateOf(true) }
    var draftAutomationId by remember { mutableStateOf("") }
    var draftPreventAutoExecute by remember { mutableStateOf(false) }

    fun persist(next: List<QuickReplySlot>) {
        store.save(store.load(presetName).copy(slots = next))
        slots = next
    }

    fun switchPreset(name: String) {
        presetName = name
        store.setActive(name)
        slots = store.load(name).slots
    }

    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "快捷回复", onBack = onBack, sky = settingsSky)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("全局快捷回复", style = MaterialTheme.typography.titleSmall, color = EmberTheme.colors.accent)
            Text(
                "字段对齐官方 Quick Reply 扩展：目录多预设（data/default-user/quick-replies/*.json）。槽位 = 斜杠链 mes + label + 启用。",
                style = MaterialTheme.typography.bodySmall,
                color = EmberTheme.colors.inkMute,
                modifier = Modifier.padding(top = 4.dp),
            )
            FlowRow(
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Box {
                    FilterChip(
                        selected = false,
                        onClick = { presetMenuExpanded = true },
                        label = { Text("预设：${presetName}") },
                    )
                    DropdownMenu(expanded = presetMenuExpanded, onDismissRequest = { presetMenuExpanded = false }) {
                        store.presets().forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = {
                                    presetMenuExpanded = false
                                    switchPreset(p.name)
                                },
                            )
                        }
                    }
                }
                EmberSecondaryButton(label = "新建预设", onClick = {
                    draftPresetName = ""
                    showPresetDialog = true
                }, minHeight = 40.dp)
                if (store.presets().size > 1) {
                    TextButton(onClick = {
                        store.delete(presetName)
                        val next = store.presets().firstOrNull()?.name ?: "default"
                        switchPreset(next)
                    }) { Text("删除当前") }
                }
            }
            if (slots.isEmpty()) {
                EmptyState(
                    title = "还没有快捷回复",
                    body = "点下方按钮新增，例如 /echo 你好 或 /pass 早上好。",
                    compact = true,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slots.forEach { slot ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = EmberTheme.colors.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).clickable {
                                    editing = slots.indexOfFirst { it.label == slot.label }.takeIf { it >= 0 }
                                    editing?.let {
                                        draftLabel = slot.label
                                        draftMes = slot.mes
                                        draftEnabled = slot.enabled
                                        draftAutomationId = slot.automationId
                                        draftPreventAutoExecute = slot.preventAutoExecute
                                    }
                                },
                            ) {
                                Text(
                                    slot.label.ifBlank { "（未命名）" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (slot.label.isBlank()) EmberTheme.colors.lineStrong else EmberTheme.colors.accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (slot.automationId.isNotBlank()) "⚙ ${slot.automationId} · ${slot.mes.ifBlank { "（空）" }}" else slot.mes.ifBlank { "（空）" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmberTheme.colors.inkMute,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                editing = slots.indexOfFirst { it.label == slot.label }.takeIf { it >= 0 }
                                editing?.let {
                                    draftLabel = slot.label
                                    draftMes = slot.mes
                                    draftEnabled = slot.enabled
                                    draftAutomationId = slot.automationId
                                    draftPreventAutoExecute = slot.preventAutoExecute
                                }
                            }, modifier = Modifier.size(34.dp)) {
                                Icon(FaIcons.Pencil, contentDescription = "编辑", modifier = Modifier.size(17.dp), tint = EmberTheme.colors.lineStrong)
                            }
                            IconButton(onClick = {
                                persist(slots.filterNot { it.label == slot.label })
                            }, modifier = Modifier.size(34.dp)) {
                                Icon(FaIcons.TrashCan, contentDescription = "删除", modifier = Modifier.size(17.dp), tint = EmberTheme.colors.danger)
                            }
                            EmberSwitch(
                                checked = slot.enabled,
                                onChange = { on ->
                                    persist(slots.map { if (it.label == slot.label) it.copy(enabled = on) else it })
                                },
                            )
                        }
                    }
                }
            }
            EmberPrimaryButton(
                label = "新增快捷回复",
                onClick = {
                    adding = true
                    draftLabel = ""
                    draftMes = ""
                    draftEnabled = true
                    draftAutomationId = ""
                    draftPreventAutoExecute = false
                },
                icon = FaIcons.Plus,
                expandWidth = true,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
    }

    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("新建快捷回复预设") },
            text = {
                ShellInput(
                    value = draftPresetName,
                    onValueChange = { draftPresetName = it },
                    label = "预设名",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = draftPresetName.trim()
                    if (name.isNotEmpty()) {
                        store.save(com.emberinn.engine.slash.QuickReplyPreset(name = name, slots = emptyList()))
                        switchPreset(name)
                    } else {
                        Toast.makeText(context, "预设名不能为空", Toast.LENGTH_SHORT).show()
                    }
                    showPresetDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPresetDialog = false }) { Text("取消") }
            },
        )
    }

    if (adding || editing != null) {
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            title = { Text(if (adding) "新增快捷回复" else "编辑快捷回复") },
            text = {
                Column {
                    ShellInput(
                        value = draftLabel,
                        onValueChange = { draftLabel = it },
                        label = "按钮文案",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ShellInput(
                        value = draftMes,
                        onValueChange = { draftMes = it },
                        label = "斜杠链（如 /echo 你好）",
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("启用", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        EmberSwitch(checked = draftEnabled, onChange = { draftEnabled = it })
                    }
                    ShellInput(
                        value = draftAutomationId,
                        onValueChange = { draftAutomationId = it },
                        label = "automationId（与世界书条目关联自动执行）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("自动执行期间禁止嵌套自动执行", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        EmberSwitch(checked = draftPreventAutoExecute, onChange = { draftPreventAutoExecute = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val label = draftLabel.trim()
                    if (label.isNotEmpty()) {
                        val slot = QuickReplySlot(mes = draftMes, label = label, enabled = draftEnabled, automationId = draftAutomationId.trim(), preventAutoExecute = draftPreventAutoExecute)
                        val next = if (adding) slots + slot else slots.mapIndexed { i, s -> if (i == editing) slot else s }
                        persist(next)
                    } else {
                        Toast.makeText(context, "按钮文案不能为空", Toast.LENGTH_SHORT).show()
                    }
                    adding = false
                    editing = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { adding = false; editing = null }) { Text("取消") }
            },
        )
    }
}
