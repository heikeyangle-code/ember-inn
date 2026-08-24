package com.emberinn.app.ui.settings


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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberinn.app.data.QuickReplyStore
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.EmberSwitch
import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.ShellActionButton
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.icons.FaIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import com.emberinn.engine.slash.QuickReplySlot

/**
 * 全局快捷回复（对齐官方 Quick Reply 扩展：预设 + 槽位 mes/label/enabled/automationId/preventAutoExecute）。
 * 官方存储 preset 文件；本页读写 filesDir/quick-replies.json（QuickReplyPreset），执行走 QuickReplyExecutor。
 */
@Composable
fun QuickRepliesScreen(onBack: () -> Unit) {
    val c = EmberTheme.colors
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

    fun loadDraft(slot: QuickReplySlot?) {
        if (slot == null) return
        draftLabel = slot.label
        draftMes = slot.mes
        draftEnabled = slot.enabled
        draftAutomationId = slot.automationId
        draftPreventAutoExecute = slot.preventAutoExecute
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
                .padding(horizontal = 20.dp),
        ) {
            Text(
                "字段对齐官方 Quick Reply 扩展：目录多预设（data/default-user/quick-replies/*.json）。槽位 = 斜杠链 mes + label + 启用。",
                color = c.inkMute,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            GroupLabel("预设")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.surfaceSink)
                            .clickable { presetMenuExpanded = true }
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                    ) {
                        Text("当前：$presetName", color = c.ink, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(FaIcons.ChevronDown, contentDescription = null, tint = c.inkMute, modifier = Modifier.size(13.dp))
                    }
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
                Spacer(Modifier.width(10.dp))
                ShellActionButton(label = "新建预设") {
                    draftPresetName = ""
                    showPresetDialog = true
                }
                if (store.presets().size > 1) {
                    Spacer(Modifier.width(10.dp))
                    Text("删除当前", color = c.danger, fontSize = 13.sp, modifier = Modifier.clickable {
                        store.delete(presetName)
                        val next = store.presets().firstOrNull()?.name ?: "default"
                        switchPreset(next)
                    }.padding(4.dp))
                }
            }
            GroupLabel("槽位")
            if (slots.isEmpty()) {
                Text(
                    "还没有快捷回复。点下方按钮新增，例如 /echo 你好 或 /pass 早上好。",
                    color = c.inkMute,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            slots.forEachIndexed { index, slot ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                loadDraft(slot)
                                editing = index
                            },
                    ) {
                        Text(
                            slot.label.ifBlank { "（未命名）" },
                            color = if (slot.label.isBlank()) c.inkMute else c.ink,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (slot.automationId.isNotBlank()) "⚙ ${slot.automationId} · ${slot.mes.ifBlank { "（空）" }}" else slot.mes.ifBlank { "（空）" },
                            color = c.inkMute,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        FaIcons.Pencil,
                        contentDescription = "编辑",
                        tint = c.inkMute,
                        modifier = Modifier
                            .size(17.dp)
                            .clickable {
                                loadDraft(slot)
                                editing = index
                            }
                            .padding(2.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        FaIcons.TrashCan,
                        contentDescription = "删除",
                        tint = c.danger,
                        modifier = Modifier
                            .size(17.dp)
                            .clickable { persist(slots.filterNot { it.label == slot.label }) }
                            .padding(2.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    EmberSwitch(
                        checked = slot.enabled,
                        onChange = { on ->
                            persist(slots.map { if (it.label == slot.label) it.copy(enabled = on) else it })
                        },
                    )
                }
            }
            ShellActionButton(
                label = "＋ 新增快捷回复",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                adding = true
                draftLabel = ""
                draftMes = ""
                draftEnabled = true
                draftAutomationId = ""
                draftPreventAutoExecute = false
            }
            Spacer(Modifier.height(120.dp))
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
                        Text("启用", color = EmberTheme.colors.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
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
                        Text("自动执行期间禁止嵌套自动执行", color = EmberTheme.colors.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
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
