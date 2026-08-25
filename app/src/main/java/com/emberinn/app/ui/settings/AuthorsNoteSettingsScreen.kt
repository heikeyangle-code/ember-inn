package com.emberinn.app.ui.settings


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.EmberSwitch
import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.ShellActionButton
import com.emberinn.app.ui.design.components.ShellChip
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.icons.FaIcons

/**
 * 作者注释全局默认（官方 authors-note.js extension_settings.note）。
 * 字段顺序对齐官方 index.html floating 面板（L7744+）：内容 → 允许WI扫描 → 位置 → 深度 → 间隔 → 角色。
 */
@Composable
fun AuthorsNoteSettingsScreen(onBack: () -> Unit) {
    val c = EmberTheme.colors
    val context = LocalContext.current
    var prefs by remember { mutableStateOf(AuthorsNotePrefsStore.load(context)) }
    fun save() = AuthorsNotePrefsStore.save(context, prefs)

    SettingsGlassPage { sky ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SettingsTopBar(title = "作者注释", onBack = onBack, sky = sky)
            // 官方序①：内容（extension_floating_prompt）
            ShellInput(
                value = prefs.defaultPrompt,
                onValueChange = { prefs = prefs.copy(defaultPrompt = it); save() },
                label = "默认内容（聊天未设置时使用）",
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            // 官方序②：允许世界书扫描（extension_floating_allow_wi_scan）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            ) {
                Text("允许世界书扫描（allowWIScan）", color = c.ink, fontSize = EmberTheme.typo.subhead.fontSize, modifier = Modifier.weight(1f))
                EmberSwitch(checked = prefs.allowWIScan, onChange = { prefs = prefs.copy(allowWIScan = it); save() })
            }
            // 官方序③：位置 radio 组（before_char=2 / after_char=0 / depth=1，值随引擎差分锁定口径）
            GroupLabel("默认位置")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShellChip("提示词内", selected = prefs.defaultPosition == 0) { prefs = prefs.copy(defaultPosition = 0); save() }
                ShellChip("对话内", selected = prefs.defaultPosition == 1) { prefs = prefs.copy(defaultPosition = 1); save() }
                ShellChip("提示词前", selected = prefs.defaultPosition == 2) { prefs = prefs.copy(defaultPosition = 2); save() }
            }
            // 官方序④：深度；⑤：间隔
            ShellInput(
                value = prefs.defaultDepth.toString(),
                onValueChange = { prefs = prefs.copy(defaultDepth = it.toIntOrNull() ?: 4); save() },
                label = "默认深度",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            ShellInput(
                value = prefs.defaultInterval.toString(),
                onValueChange = { prefs = prefs.copy(defaultInterval = it.toIntOrNull() ?: 1); save() },
                label = "默认间隔（每 N 条用户消息）",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            // 官方序⑥：角色（system/user/assistant）
            GroupLabel("默认角色")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShellChip("系统", selected = prefs.defaultRole == 0) { prefs = prefs.copy(defaultRole = 0); save() }
                ShellChip("用户", selected = prefs.defaultRole == 1) { prefs = prefs.copy(defaultRole = 1); save() }
                ShellChip("助手", selected = prefs.defaultRole == 2) { prefs = prefs.copy(defaultRole = 2); save() }
            }
            GroupLabel("角色备注")
            Text(
                "按角色名编辑；聊天 ⋮ 作者注释里也可为当前角色设置。",
                color = c.inkMute,
                fontSize = EmberTheme.typo.caption.fontSize,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            prefs.charaNotes.forEach { (name, note) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, color = c.ink, fontSize = EmberTheme.typo.subhead.fontSize)
                        Text(
                            if (note.useChara) "启用 · ${note.prompt.take(24)}" else "未启用",
                            color = c.inkMute,
                            fontSize = EmberTheme.typo.caption.fontSize,
                        )
                    }
                    Icon(
                        FaIcons.TrashCan,
                        contentDescription = "删除角色备注",
                        tint = c.danger,
                        modifier = Modifier
                            .size(17.dp)
                            .clickable { prefs = prefs.copy(charaNotes = prefs.charaNotes - name); save() }
                            .padding(2.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                ShellActionButton(label = "保存全部修改", modifier = Modifier.weight(1f)) { save() }
                TextButton(onClick = onBack) { Text("完成", color = c.accent) }
            }
            Spacer(Modifier.height(120.dp))
        }
    }
}
