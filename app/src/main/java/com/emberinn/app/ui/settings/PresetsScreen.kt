package com.emberinn.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.emberinn.engine.prompt.PresetLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import com.emberinn.app.ui.icons.PhosphorIcons
import kotlinx.serialization.json.jsonObject

/**
 * 预设管理器（官方 default/content/presets，PresetLibrary 打包）：
 * 上下文模板 / 指导模板 / 采样预设 / 系统提示 / 推理预设。
 * context/instruct 的消费点是 textgen 后端（未接，登记）；sampler 预设直接应用到提供商详情页。
 */
@Composable
fun PresetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var prefs by remember { mutableStateOf(PresetPrefsStore.load(context)) }
    var userPresets by remember { mutableStateOf(
        mapOf(
            "context" to UserPresetStore.list(context, "context"),
            "instruct" to UserPresetStore.list(context, "instruct"),
            "sampler" to UserPresetStore.list(context, "sampler"),
            "sysprompt" to UserPresetStore.list(context, "sysprompt"),
            "reasoning" to UserPresetStore.list(context, "reasoning"),
        ),
    ) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    val presetImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty()
        val parsed = runCatching { kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.parseToJsonElement(text).jsonObject }.getOrNull()
        if (parsed == null) {
            importMessage = "导入失败：不是有效 JSON"
            return@rememberLauncherForActivityResult
        }
        val type = UserPresetStore.detectType(parsed)
        if (type == null) {
            importMessage = "导入失败：无法识别预设类型（需要 context/instruct/sampler/sysprompt/reasoning 结构）"
            return@rememberLauncherForActivityResult
        }
        val fileName = uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".json").orEmpty()
            .ifBlank { "preset" }
        val ok = UserPresetStore.save(context, type, fileName, text)
        importMessage = if (ok) "已导入：$type / $fileName" else "导入失败：文件名无效"
        userPresets = userPresets + (type to UserPresetStore.list(context, type))
    }

    SettingsGlassPage { sky ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SettingsTopBar(title = "预设", onBack = onBack, sky = sky)
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                TextButton(onClick = { presetImporter.launch(arrayOf("application/json")) }) {
                    Text("导入预设 JSON")
                }
                importMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            PresetSection(
                title = "上下文模板（context）",
                items = PresetLibrary.contextPresets().map { it.preset to false } +
                    userPresets["context"].orEmpty().map { it to true },
                selected = prefs.contextPreset,
                onSelect = { prefs = prefs.copy(contextPreset = it) },
                onDeleteUser = { name ->
                    UserPresetStore.delete(context, "context", name)
                    userPresets = userPresets + ("context" to UserPresetStore.list(context, "context"))
                },
            )
            PresetSection(
                title = "指导模板（instruct）",
                items = PresetLibrary.instructPresets().map { it.preset to false } +
                    userPresets["instruct"].orEmpty().map { it to true },
                selected = prefs.instructPreset,
                onSelect = { prefs = prefs.copy(instructPreset = it) },
                onDeleteUser = { name ->
                    UserPresetStore.delete(context, "instruct", name)
                    userPresets = userPresets + ("instruct" to UserPresetStore.list(context, "instruct"))
                },
            )
            PresetSection(
                title = "采样预设（OpenAI）",
                items = listOf("" to false) +
                    PresetLibrary.samplerPresets("openai").map { it.name to false } +
                    userPresets["sampler"].orEmpty().map { it to true },
                selected = prefs.samplerPreset,
                onSelect = { prefs = prefs.copy(samplerPreset = it) },
                emptyLabel = "默认（不应用）",
                onDeleteUser = { name ->
                    UserPresetStore.delete(context, "sampler", name)
                    userPresets = userPresets + ("sampler" to UserPresetStore.list(context, "sampler"))
                },
            )
            PresetSection(
                title = "系统提示预设（sysprompt）",
                items = listOf("" to false) +
                    PresetLibrary.systemPromptPresets().map { it.name to false } +
                    userPresets["sysprompt"].orEmpty().map { it to true },
                selected = prefs.syspromptPreset,
                onSelect = { prefs = prefs.copy(syspromptPreset = it) },
                emptyLabel = "默认（不应用）",
                onDeleteUser = { name ->
                    UserPresetStore.delete(context, "sysprompt", name)
                    userPresets = userPresets + ("sysprompt" to UserPresetStore.list(context, "sysprompt"))
                },
            )
            PresetSection(
                title = "推理预设（reasoning）",
                items = listOf("" to false) +
                    PresetLibrary.reasoningPresets().map { it.name to false } +
                    userPresets["reasoning"].orEmpty().map { it to true },
                selected = prefs.reasoningPreset,
                onSelect = { prefs = prefs.copy(reasoningPreset = it) },
                emptyLabel = "默认（不应用）",
                onDeleteUser = { name ->
                    UserPresetStore.delete(context, "reasoning", name)
                    userPresets = userPresets + ("reasoning" to UserPresetStore.list(context, "reasoning"))
                },
            )
            Text(
                "上下文/指导模板的消费点是 textgen 后端（尚未实现，选择先保存）；" +
                    "采样预设在“提供商与模型”详情页可直接应用；sysprompt/reasoning 与 Prompt Manager 一起待接。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 12.dp),
            )
            androidx.compose.material3.Button(
                onClick = { PresetPrefsStore.save(context, prefs) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("保存") }
        }
    }
}

@Composable
private fun PresetSection(
    title: String,
    items: List<Pair<String, Boolean>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDeleteUser: (String) -> Unit,
    emptyLabel: String? = null,
) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
    items.forEach { (name, isUser) ->
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(name) }
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            Text(
                if (name.isEmpty()) (emptyLabel ?: "默认") else name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected == name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected == name) {
                Text("✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (isUser) {
                IconButton(onClick = { onDeleteUser(name) }, modifier = Modifier.size(28.dp)) {
                    Icon(PhosphorIcons.Delete, contentDescription = "删除用户预设", modifier = Modifier.size(14.dp))
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 4.dp))
    }
}
