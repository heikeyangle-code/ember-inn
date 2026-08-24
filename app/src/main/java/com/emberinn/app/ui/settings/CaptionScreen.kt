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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.ShellChip

/** 图片描述（caption）设置：对齐官方 extensions/caption settings.html 核心字段。 */
@Composable
fun CaptionScreen(onBack: () -> Unit) {
    val c = EmberTheme.colors
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
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    "对齐官方 caption 扩展：添加图片后，聊天输入区点“图片描述”生成描述并发送（sendCaptionedMessage 语义）。multimodal 用当前模型；local/extras/horde 走 sourceUrl 代理端点。",
                    color = c.inkMute,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                ) {
                    Text("启用", color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    EmberSwitch(checked = s.enabled, onChange = { s = s.copy(enabled = it); save() })
                }
                GroupLabel("来源")
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    ShellChip("Multimodal", selected = s.source == "multimodal") { s = s.copy(source = "multimodal"); save() }
                    Spacer(Modifier.width(7.dp))
                    ShellChip("Local", selected = s.source == "local") { s = s.copy(source = "local"); save() }
                    Spacer(Modifier.width(7.dp))
                    ShellChip("Extras", selected = s.source == "extras") { s = s.copy(source = "extras"); save() }
                    Spacer(Modifier.width(7.dp))
                    ShellChip("Horde", selected = s.source == "horde") { s = s.copy(source = "horde"); save() }
                }
                if (s.source != "multimodal") {
                    ShellInput(
                        value = s.sourceUrl,
                        onValueChange = { s = s.copy(sourceUrl = it); save() },
                        label = "服务基址（sourceUrl，如 https://my-sillytavern.local）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ShellInput(
                    value = s.prompt,
                    onValueChange = { s = s.copy(prompt = it); save() },
                    label = "描述提示词（prompt）",
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                ShellInput(
                    value = s.template,
                    onValueChange = { s = s.copy(template = it); save() },
                    label = "消息模板（template；缺 {{caption}} 自动补）",
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                ) {
                    Text("聊天内显示图片（show_in_chat）", color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    EmberSwitch(checked = s.showInChat, onChange = { s = s.copy(showInChat = it); save() })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                ) {
                    Text("发送前人工确认（refine_mode）", color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    EmberSwitch(checked = s.refineMode, onChange = { s = s.copy(refineMode = it); save() })
                }
                Text(
                    "refine_mode 当前登记为 UI 开关；App 侧确认弹层未接。prompt_ask（每次询问提示词）暂未接。",
                    color = c.inkMute,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
                Spacer(Modifier.height(120.dp))
            }
        }
    }
}
