package com.emberinn.app.ui.settings

import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.EmberSwitch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.ShellChip
import com.emberinn.engine.prompt.MemoryEngine

/** 记忆扩展设置（对齐官方 extensions/memory settings.html 全部字段；UI 沿用月帷新语言）。 */
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val c = EmberTheme.colors
    val context = LocalContext.current
    var s by remember { mutableStateOf(MemoryPrefs.load(context)) }
    fun save() = MemoryPrefs.save(context, s)

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "记忆扩展", onBack = onBack, sky = settingsSky)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    "字段对齐官方 memory 扩展（settings.html）。总结源当前支持 main（当前模型）；extras/webllm 未接。聊天 ⋮ 菜单可立即总结。",
                    color = c.inkMute,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                GroupLabel("摘要")
                ToggleRow("冻结记忆（memoryFrozen）", s.memoryFrozen) { s = s.copy(memoryFrozen = it); save() }
                ToggleRow("总结时跳过世界书（SkipWIAN）", s.skipWIAN) { s = s.copy(skipWIAN = it); save() }
                ShellInput(
                    value = s.prompt,
                    onValueChange = { s = s.copy(prompt = it); save() },
                    label = "总结提示词（{{words}} 会被词数替换）",
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.padding(vertical = 6.dp)) {
                    TextButton(onClick = { s = s.copy(prompt = MemoryEngine.DEFAULT_PROMPT); save() }) {
                        Text("恢复默认提示词", color = c.accent)
                    }
                }
                ShellInput(
                    value = s.template,
                    onValueChange = { s = s.copy(template = it); save() },
                    label = "注入模板（{{summary}} 替换为摘要；留空=Summary: 文本）",
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                GroupLabel("注入")
                ChipRow(
                    listOf("角色后(0)" to 0, "深度注入(1)" to 1, "角色前(2)" to 2, "不注入(-1)" to -1),
                    s.position,
                ) { s = s.copy(position = it); save() }
                ChipRow(
                    listOf("system(0)" to 0, "user(1)" to 1, "assistant(2)" to 2),
                    s.role,
                ) { s = s.copy(role = it); save() }
                NumberRow("注入深度（depth）", s.depth.toString()) { v ->
                    s = s.copy(depth = v.toIntOrNull() ?: 2); save()
                }
                ToggleRow("包含世界书扫描（scan）", s.scan) { s = s.copy(scan = it); save() }

                GroupLabel("总结条件")
                NumberRow("摘要词数上限（promptWords）", s.promptWords.toString()) { v ->
                    s = s.copy(promptWords = v.toIntOrNull() ?: 200); save()
                }
                NumberRow("消息间隔（promptInterval；0=关闭自动）", s.promptInterval.toString()) { v ->
                    s = s.copy(promptInterval = v.toIntOrNull() ?: 10); save()
                }
                NumberRow("强制词数（promptForceWords；0=关闭）", s.promptForceWords.toString()) { v ->
                    s = s.copy(promptForceWords = v.toIntOrNull() ?: 0); save()
                }
                NumberRow("每次请求最大消息数（0=不限制）", s.maxMessagesPerRequest.toString()) { v ->
                    s = s.copy(maxMessagesPerRequest = v.toIntOrNull() ?: 0); save()
                }
                NumberRow("总结响应长度（0=跟随模型）", s.overrideResponseLength.toString()) { v ->
                    s = s.copy(overrideResponseLength = v.toIntOrNull() ?: 0); save()
                }

                GroupLabel("总结构建器")
                ChipRow(
                    listOf("DEFAULT(0)" to 0, "RAW 阻塞(1)" to 1, "RAW 非阻塞(2)" to 2),
                    s.promptBuilder,
                ) { s = s.copy(promptBuilder = it); save() }
                Text(
                    "DEFAULT=官方 generateQuietPrompt（当前上下文+quiet 提示）；RAW=官方 getRawSummaryPrompt + generateRaw。",
                    color = c.inkMute,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
                Spacer(Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun ChipRow(options: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        options.forEach { (label, value) ->
            ShellChip(label, selected = selected == value) { onSelect(value) }
        }
    }
}

@Composable
private fun NumberRow(label: String, value: String, onChange: (String) -> Unit) {
    ShellInput(
        value = value,
        onValueChange = onChange,
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
    ) {
        Text(label, color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        EmberSwitch(checked = checked, onChange = onChange)
    }
}
