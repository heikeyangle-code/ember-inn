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

/**
 * 预设管理器（官方 default/content/presets，PresetLibrary 打包）：
 * 上下文模板 / 指导模板 / 采样预设 / 系统提示 / 推理预设。
 * context/instruct 的消费点是 textgen 后端（未接，登记）；sampler 预设直接应用到提供商详情页。
 */
@Composable
fun PresetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var prefs by remember { mutableStateOf(PresetPrefsStore.load(context)) }

    SettingsGlassPage { sky ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SettingsTopBar(title = "预设", onBack = onBack, sky = sky)
            PresetSection(
                title = "上下文模板（context）",
                items = PresetLibrary.contextPresets().map { it.preset },
                selected = prefs.contextPreset,
                onSelect = { prefs = prefs.copy(contextPreset = it) },
            )
            PresetSection(
                title = "指导模板（instruct）",
                items = PresetLibrary.instructPresets().map { it.preset },
                selected = prefs.instructPreset,
                onSelect = { prefs = prefs.copy(instructPreset = it) },
            )
            PresetSection(
                title = "采样预设（OpenAI）",
                items = listOf("") + PresetLibrary.samplerPresets("openai").map { it.name },
                selected = prefs.samplerPreset,
                onSelect = { prefs = prefs.copy(samplerPreset = it) },
                emptyLabel = "默认（不应用）",
            )
            PresetSection(
                title = "系统提示预设（sysprompt）",
                items = listOf("") + PresetLibrary.systemPromptPresets().map { it.name },
                selected = prefs.syspromptPreset,
                onSelect = { prefs = prefs.copy(syspromptPreset = it) },
                emptyLabel = "默认（不应用）",
            )
            PresetSection(
                title = "推理预设（reasoning）",
                items = listOf("") + PresetLibrary.reasoningPresets().map { it.name },
                selected = prefs.reasoningPreset,
                onSelect = { prefs = prefs.copy(reasoningPreset = it) },
                emptyLabel = "默认（不应用）",
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
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    emptyLabel: String? = null,
) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
    items.forEach { name ->
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
        }
        HorizontalDivider(modifier = Modifier.padding(start = 4.dp))
    }
}
