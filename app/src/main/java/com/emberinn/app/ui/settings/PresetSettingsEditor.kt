package com.emberinn.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.engine.prompt.InstructSettings
import com.emberinn.engine.prompt.NamesBehavior
import com.emberinn.engine.prompt.PresetApplyEngine
import com.emberinn.engine.prompt.ReasoningSettings

/**
 * 官方 Advanced Formatting 表单等价物：显示/编辑“当前生效设置”（预设选中后填进表单）。
 * 官方位置：index.html AdvancedFormatting 抽屉（story string/序列文本框 + checkbox 开关 + 数字输入）。
 * 引擎判定不变；这里只编辑 App 落盘的状态（PresetSettingsStore.update 同步真实消费位点）。
 */
@Composable
fun AppliedPresetEditor(
    type: String,
    state: PresetSettingsState,
    onChange: (PresetSettingsState) -> Unit,
) {
    Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
        when (type) {
            "context" -> {
                val c = state.context
                val g = state.contextGlobals
                EditMulti("story_string（上下文模板）", c.storyString) { onChange(state.copy(context = c.copy(storyString = it))) }
                EditText("example_separator（示例分隔符）", c.exampleSeparator) { onChange(state.copy(context = c.copy(exampleSeparator = it))) }
                EditText("chat_start", c.chatStart) { onChange(state.copy(context = c.copy(chatStart = it))) }
                Text("story_string_position（官方 select：0=默认顶部 / 1=In-chat @ Depth）", style = MaterialTheme.typography.labelSmall, color = EmberTheme.colors.inkVariant, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "0（默认顶部）", 1 to "1（In-chat @ Depth）").forEach { (v, label) ->
                        FilterChip(
                            selected = c.storyStringPosition == v,
                            onClick = { onChange(state.copy(context = c.copy(storyStringPosition = v))) },
                            label = { Text(label) },
                        )
                    }
                }
                EditInt("story_string_depth", c.storyStringDepth) { onChange(state.copy(context = c.copy(storyStringDepth = it))) }
                EditInt("story_string_role", c.storyStringRole) { onChange(state.copy(context = c.copy(storyStringRole = it))) }
                EditSwitch("use_stop_strings", c.useStopStrings) { onChange(state.copy(context = c.copy(useStopStrings = it))) }
                EditSwitch("names_as_stop_strings", c.namesAsStopStrings) { onChange(state.copy(context = c.copy(namesAsStopStrings = it))) }
                EditSwitch("always_force_name2", g.alwaysForceName2) { onChange(state.copy(contextGlobals = g.copy(alwaysForceName2 = it))) }
                EditSwitch("trim_sentences", g.trimSentences) { onChange(state.copy(contextGlobals = g.copy(trimSentences = it))) }
                EditSwitch("single_line", g.singleLine) { onChange(state.copy(contextGlobals = g.copy(singleLine = it))) }
            }
            "instruct" -> {
                val i = state.instruct
                EditSwitch("enabled", i.enabled) { onChange(state.copy(instruct = i.copy(enabled = it))) }
                EditText("input_sequence", i.inputSequence) { onChange(state.copy(instruct = i.copy(inputSequence = it))) }
                EditText("output_sequence", i.outputSequence) { onChange(state.copy(instruct = i.copy(outputSequence = it))) }
                EditText("input_suffix", i.inputSuffix) { onChange(state.copy(instruct = i.copy(inputSuffix = it))) }
                EditText("output_suffix", i.outputSuffix) { onChange(state.copy(instruct = i.copy(outputSuffix = it))) }
                EditText("system_sequence", i.systemSequence) { onChange(state.copy(instruct = i.copy(systemSequence = it))) }
                EditText("system_suffix", i.systemSuffix) { onChange(state.copy(instruct = i.copy(systemSuffix = it))) }
                EditText("last_system_sequence", i.lastSystemSequence) { onChange(state.copy(instruct = i.copy(lastSystemSequence = it))) }
                EditText("first_input_sequence", i.firstInputSequence) { onChange(state.copy(instruct = i.copy(firstInputSequence = it))) }
                EditText("last_input_sequence", i.lastInputSequence) { onChange(state.copy(instruct = i.copy(lastInputSequence = it))) }
                EditText("first_output_sequence", i.firstOutputSequence) { onChange(state.copy(instruct = i.copy(firstOutputSequence = it))) }
                EditText("last_output_sequence", i.lastOutputSequence) { onChange(state.copy(instruct = i.copy(lastOutputSequence = it))) }
                EditText("story_string_prefix", i.storyStringPrefix) { onChange(state.copy(instruct = i.copy(storyStringPrefix = it))) }
                EditText("story_string_suffix", i.storyStringSuffix) { onChange(state.copy(instruct = i.copy(storyStringSuffix = it))) }
                EditText("stop_sequence", i.stopSequence) { onChange(state.copy(instruct = i.copy(stopSequence = it))) }
                EditText("user_alignment_message", i.userAlignmentMessage) { onChange(state.copy(instruct = i.copy(userAlignmentMessage = it))) }
                EditText("activation_regex", i.activationRegex) { onChange(state.copy(instruct = i.copy(activationRegex = it))) }
                Text("names_behavior（官方 select：none/force/always）", style = MaterialTheme.typography.labelSmall, color = EmberTheme.colors.inkVariant, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("none" to "Never", "force" to "Groups and Past Personas", "always" to "Always").forEach { (v, label) ->
                        FilterChip(
                            selected = i.namesBehavior.value == v,
                            onClick = { onChange(state.copy(instruct = i.copy(namesBehavior = NamesBehavior.fromValue(v)))) },
                            label = { Text(label) },
                        )
                    }
                }
                EditSwitch("wrap", i.wrap) { onChange(state.copy(instruct = i.copy(wrap = it))) }
                EditSwitch("macro", i.macro) { onChange(state.copy(instruct = i.copy(macro = it))) }
                EditSwitch("bind_to_context", i.bindToContext) { onChange(state.copy(instruct = i.copy(bindToContext = it))) }
                EditSwitch("skip_examples", i.skipExamples) { onChange(state.copy(instruct = i.copy(skipExamples = it))) }
                EditSwitch("system_same_as_user", i.systemSameAsUser) { onChange(state.copy(instruct = i.copy(systemSameAsUser = it))) }
                EditSwitch("sequences_as_stop_strings", i.sequencesAsStopStrings) { onChange(state.copy(instruct = i.copy(sequencesAsStopStrings = it))) }
            }
            "sysprompt" -> {
                val sp = state.sysprompt
                EditSwitch("enabled", sp.enabled) { onChange(state.copy(sysprompt = sp.copy(enabled = it))) }
                EditText("name", sp.name) { onChange(state.copy(sysprompt = sp.copy(name = it))) }
                EditMulti("content", sp.content) { onChange(state.copy(sysprompt = sp.copy(content = it))) }
                EditMulti("post_history", sp.postHistory) { onChange(state.copy(sysprompt = sp.copy(postHistory = it))) }
            }
            "reasoning" -> {
                val r = state.reasoning
                EditText("name", r.name) { onChange(state.copy(reasoning = r.copy(name = it))) }
                EditSwitch("auto_parse（自动解析并剥离思考块，官方默认关）", r.autoParse) { onChange(state.copy(reasoning = r.copy(autoParse = it))) }
                EditSwitch("add_to_prompts（历史思考注入提示词，官方默认关）", r.addToPrompts) { onChange(state.copy(reasoning = r.copy(addToPrompts = it))) }
                EditSwitch("auto_expand（自动展开思考，官方默认关）", r.autoExpand) { onChange(state.copy(reasoning = r.copy(autoExpand = it))) }
                EditSwitch("show_hidden（显示隐藏思考，官方默认关）", r.showHidden) { onChange(state.copy(reasoning = r.copy(showHidden = it))) }
                EditInt("max_additions（非 prefix 注入次数上限，官方默认 1）", r.maxAdditions) { onChange(state.copy(reasoning = r.copy(maxAdditions = it))) }
                EditText("prefix", r.template.prefix) { onChange(state.copy(reasoning = r.copy(template = r.template.copy(prefix = it)))) }
                EditText("suffix", r.template.suffix) { onChange(state.copy(reasoning = r.copy(template = r.template.copy(suffix = it)))) }
                EditMulti("separator", r.template.separator) { onChange(state.copy(reasoning = r.copy(template = r.template.copy(separator = it)))) }
            }
        }
    }
}

@Composable
private fun EditText(label: String, value: String, onChange: (String) -> Unit) {
    EmberTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun EditMulti(label: String, value: String, onChange: (String) -> Unit) {
    EmberTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        minLines = 2,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun EditInt(label: String, value: Int, onChange: (Int) -> Unit) {
    // 标签上置、输入框全宽：避免长说明把输入框挤成窄条（“被说明文字压变形”）
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = EmberTheme.colors.inkVariant)
        EmberTextField(
            value = value.toString(),
            onValueChange = { it.toIntOrNull()?.let(onChange) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

@Composable
private fun EditSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        EmberSwitch(checked = checked, onCheckedChange = onChange)
    }
}
