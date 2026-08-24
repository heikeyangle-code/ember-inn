package com.emberinn.app.ui.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.icons.FaIcons
import androidx.compose.material3.Icon

/** 作者注释全局默认（官方 authors-note.js extension_settings.note）。 */
@Composable
fun AuthorsNoteSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var prefs by remember { mutableStateOf(AuthorsNotePrefsStore.load(context)) }

    SettingsGlassPage { sky ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SettingsTopBar(title = "作者注释", onBack = onBack, sky = sky)
            ShellInput(
                value = prefs.defaultPrompt,
                onValueChange = { prefs = prefs.copy(defaultPrompt = it) },
                label = "默认内容（聊天未设置时使用）",
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                "默认位置",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 14.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                FilterChip(selected = prefs.defaultPosition == 0, onClick = { prefs = prefs.copy(defaultPosition = 0) }, label = { Text("提示词内") })
                FilterChip(selected = prefs.defaultPosition == 1, onClick = { prefs = prefs.copy(defaultPosition = 1) }, label = { Text("对话内") })
                FilterChip(selected = prefs.defaultPosition == 2, onClick = { prefs = prefs.copy(defaultPosition = 2) }, label = { Text("提示词前") })
            }
            ShellInput(
                value = prefs.defaultDepth.toString(),
                onValueChange = { prefs = prefs.copy(defaultDepth = it.toIntOrNull() ?: 4) },
                label = "默认深度",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ShellInput(
                value = prefs.defaultInterval.toString(),
                onValueChange = { prefs = prefs.copy(defaultInterval = it.toIntOrNull() ?: 1) },
                label = "默认间隔（每 N 条用户消息）",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                "默认角色",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 14.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                FilterChip(selected = prefs.defaultRole == 0, onClick = { prefs = prefs.copy(defaultRole = 0) }, label = { Text("系统") })
                FilterChip(selected = prefs.defaultRole == 1, onClick = { prefs = prefs.copy(defaultRole = 1) }, label = { Text("用户") })
                FilterChip(selected = prefs.defaultRole == 2, onClick = { prefs = prefs.copy(defaultRole = 2) }, label = { Text("助手") })
            }
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("允许世界书扫描（allowWIScan）", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = prefs.allowWIScan, onCheckedChange = { prefs = prefs.copy(allowWIScan = it) })
            }
            Text(
                "角色备注（按角色名编辑，聊天 ⋮ 作者注释里也可为当前角色设置）",
                style = MaterialTheme.typography.bodySmall,
                color = EmberTheme.colors.lineStrong,
                modifier = Modifier.padding(top = 12.dp),
            )
            prefs.charaNotes.forEach { (name, note) ->
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (note.useChara) "启用 · ${note.prompt.take(24)}" else "未启用",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmberTheme.colors.inkMute,
                        )
                    }
                    IconButton(onClick = {
                        prefs = prefs.copy(charaNotes = prefs.charaNotes - name)
                    }) {
                        Icon(FaIcons.TrashCan, contentDescription = "删除角色备注", modifier = Modifier.size(16.dp))
                    }
                }
            }
            androidx.compose.material3.Button(
                onClick = { AuthorsNotePrefsStore.save(context, prefs) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("保存") }
        }
    }
}
