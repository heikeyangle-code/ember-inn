package com.emberinn.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.ExpressionStore
import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberPrimaryButton
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.icons.FaIcons
import java.io.File

/** 表情精灵管理（对齐官方 extensions/expressions：角色精灵文件 + 选择设置）。 */
@Composable
fun ExpressionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ExpressionStore(context) }
    val characters = remember { CharacterStore(context).list() }
    var prefs by remember { mutableStateOf(ExpressionPrefs.load(context)) }
    var selectedId by remember { mutableStateOf(characters.firstOrNull()?.id ?: "") }
    val selectedName = characters.firstOrNull { it.id == selectedId }?.name ?: ""
    var sprites by remember { mutableStateOf(if (selectedName.isBlank()) emptyList() else store.sprites(selectedName)) }
    fun save() = ExpressionPrefs.save(context, prefs)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && selectedName.isNotBlank()) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
                val name = runCatching {
                    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                }.getOrNull() ?: "sprite.png"
                store.saveSprite(selectedName, name, bytes)
                sprites = store.sprites(selectedName)
            }
        }
    }

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "表情精灵", onBack = onBack, sky = settingsSky)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("表情精灵", style = MaterialTheme.typography.titleSmall, color = EmberTheme.colors.accent)
                    Text(
                        "对齐官方 expressions 扩展：精灵按角色存放在 expressions/{角色名}/，AI 消息正文自动分类选图。",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmberTheme.colors.inkVariant,
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("启用", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        EmberSwitch(checked = prefs.enabled, onCheckedChange = { prefs = prefs.copy(enabled = it); save() })
                    }
                    EmberTextField(
                        value = prefs.fallbackExpression,
                        onValueChange = { prefs = prefs.copy(fallbackExpression = it); save() },
                        label = { Text("兜底表情（fallbackExpression；空=无）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("多立绘随机（allowMultiple）", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        EmberSwitch(checked = prefs.allowMultiple, onCheckedChange = { prefs = prefs.copy(allowMultiple = it); save() })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("避免与上一张重复（rerollIfSame）", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        EmberSwitch(checked = prefs.rerollIfSame, onCheckedChange = { prefs = prefs.copy(rerollIfSame = it); save() })
                    }
                }
                item {
                    Text("选择角色", style = MaterialTheme.typography.labelMedium, color = EmberTheme.colors.accent)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        characters.take(8).forEach { c ->
                            FilterChip(
                                selected = c.id == selectedId,
                                onClick = {
                                    selectedId = c.id
                                    sprites = store.sprites(c.name)
                                },
                                label = { Text(c.name) },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                }
                if (selectedName.isNotBlank()) {
                    item {
                        EmberPrimaryButton(
                            label = "导入精灵图片（PNG/JPG/WebP）",
                            onClick = { picker.launch("image/*") },
                            icon = FaIcons.Plus,
                            expandWidth = true,
                        )
                    }
                }
                items(sprites, key = { it.path }) { sprite ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        Text(sprite.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            store.deleteSprite(selectedName, sprite.path)
                            sprites = store.sprites(selectedName)
                        }) {
                            Icon(FaIcons.TrashCan, contentDescription = "删除", tint = EmberTheme.colors.danger)
                        }
                    }
                }
                if (sprites.isEmpty() && selectedName.isNotBlank()) {
                    item {
                        Text(
                            "还没有精灵。文件名即表情标签（如 joy.png / sad-1.png），AI 消息正文会按表情词自动匹配。",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmberTheme.colors.lineStrong,
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
