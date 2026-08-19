package com.emberinn.app.ui.settings

import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** 图片描述（caption）设置：对齐官方 extensions/caption settings.html 核心字段。 */
@Composable
fun CaptionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var s by remember { mutableStateOf(CaptionPrefs.load(context)) }
    fun save() = CaptionPrefs.save(context, s)

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "图片描述（Caption）", onBack = onBack, sky = settingsSky)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("图片描述", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "对齐官方 caption 扩展：添加图片后，聊天输入区点“图片描述”生成描述并发送（sendCaptionedMessage 语义）。multimodal 用当前模型；local/extras/horde 走 sourceUrl 代理端点。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text("启用", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    EmberSwitch(checked = s.enabled, onCheckedChange = { s = s.copy(enabled = it); save() })
                }
                Text("来源", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    FilterChip(selected = s.source == "multimodal", onClick = { s = s.copy(source = "multimodal"); save() }, label = { Text("Multimodal") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = s.source == "local", onClick = { s = s.copy(source = "local"); save() }, label = { Text("Local") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = s.source == "extras", onClick = { s = s.copy(source = "extras"); save() }, label = { Text("Extras") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = s.source == "horde", onClick = { s = s.copy(source = "horde"); save() }, label = { Text("Horde") })
                }
                if (s.source != "multimodal") {
                    EmberTextField(
                        value = s.sourceUrl,
                        onValueChange = { s = s.copy(sourceUrl = it); save() },
                        label = { Text("服务基址（sourceUrl，如 https://my-sillytavern.local）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
                EmberTextField(
                    value = s.prompt,
                    onValueChange = { s = s.copy(prompt = it); save() },
                    label = { Text("描述提示词（prompt）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                EmberTextField(
                    value = s.template,
                    onValueChange = { s = s.copy(template = it); save() },
                    label = { Text("消息模板（template；缺 {{caption}} 自动补）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text("聊天内显示图片（show_in_chat）", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    EmberSwitch(checked = s.showInChat, onCheckedChange = { s = s.copy(showInChat = it); save() })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text("发送前人工确认（refine_mode）", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    EmberSwitch(checked = s.refineMode, onCheckedChange = { s = s.copy(refineMode = it); save() })
                }
                Text(
                    "refine_mode 当前登记为 UI 开关；App 侧确认弹层未接。prompt_ask（每次询问提示词）暂未接。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
