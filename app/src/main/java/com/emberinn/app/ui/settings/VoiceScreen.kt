package com.emberinn.app.ui.settings


import com.emberinn.app.data.TtsBackendRegistry
import com.emberinn.app.data.TtsReader
import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.ui.components.EmberSlider
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 语音页：TTS 设置，字段对齐官方 tts 扩展（settings.html）。
 * 官方 1.18 无 STT；在线 TTS 提供商是 P3 引擎层。本页引擎 = Android 系统 TTS，
 * 语音选择 / 语速 / 试听真实可用；聊天自动朗读在 P3 接入，朗读选项先持久化。
 */
@Composable
fun VoiceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by rememberSaveable { mutableStateOf(VoicePrefs.enabled(context)) }
    var voice by rememberSaveable { mutableStateOf(VoicePrefs.voice(context)) }
    var rate by rememberSaveable { mutableStateOf(VoicePrefs.rate(context)) }
    var autoGeneration by rememberSaveable { mutableStateOf(VoicePrefs.autoGeneration(context)) }
    var narrateUser by rememberSaveable { mutableStateOf(VoicePrefs.narrateUser(context)) }
    var narrateByParagraphs by rememberSaveable { mutableStateOf(VoicePrefs.narrateByParagraphs(context)) }
    var skipCodeblocks by rememberSaveable { mutableStateOf(VoicePrefs.skipCodeblocks(context)) }
    var skipTags by rememberSaveable { mutableStateOf(VoicePrefs.skipTags(context)) }
    var applyRegex by rememberSaveable { mutableStateOf(VoicePrefs.applyRegex(context)) }
    var regexPattern by rememberSaveable { mutableStateOf(VoicePrefs.regexPattern(context)) }
    var ttsProvider by rememberSaveable { mutableStateOf(VoicePrefs.ttsProvider(context)) }
    var ttsEndpoint by rememberSaveable { mutableStateOf(VoicePrefs.ttsEndpoint(context)) }
    var ttsApiKey by rememberSaveable { mutableStateOf(VoicePrefs.ttsApiKey(context)) }
    var ttsModel by rememberSaveable { mutableStateOf(VoicePrefs.ttsModel(context)) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    // 异步加载的外部后端 voice 清单（按当前 provider 刷新）
    var externalVoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var voicesLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save() = VoicePrefs.save(
        context, enabled, voice, rate, autoGeneration, narrateUser,
        narrateByParagraphs, skipCodeblocks, skipTags, applyRegex, regexPattern,
    )

    fun saveProvider(p: String, ep: String, key: String, model: String) {
        ttsProvider = p; ttsEndpoint = ep; ttsApiKey = key; ttsModel = model
        VoicePrefs.saveTtsProvider(context, p, ep, key, model)
    }

    // Android 系统 TTS 引擎：异步初始化后枚举本机声音，页面销毁时释放
    var ready by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
        }
        tts.value = engine
        onDispose { engine.shutdown() }
    }
    val voices = remember(ready) {
        tts.value?.voices?.sortedWith(compareBy({ it.locale.toString() }, { it.name })) ?: emptyList()
    }

    fun playSample() {
        val engine = tts.value ?: return
        if (!ready) return
        val selected = voices.firstOrNull { it.name == voice }
        if (selected != null && engine.setVoice(selected) == TextToSpeech.ERROR) {
            engine.setLanguage(selected.locale)
        }
        engine.setSpeechRate(rate)
        val sample = "你好，我是余烬。这是一次语音朗读试听。"
        if (engine.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "ember_tts_test") == TextToSpeech.ERROR) {
            Toast.makeText(context, "本机语音引擎不可用", Toast.LENGTH_SHORT).show()
        }
    }

    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "语音", onBack = onBack, sky = settingsSky)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("语音朗读（TTS）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "对齐官方 TTS 设置；官方 1.18 无语音输入（STT）。在线语音提供商在 P3 引擎层接入，本机引擎可直接试听。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用朗读", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "聊天自动朗读将在后续版本接入；启用后本页试听可用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    EmberSwitch(checked = enabled, onCheckedChange = { enabled = it; save() })
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Column {
                    Text(
                        "引擎与语音",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    ) {
                        Text("引擎", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            tts.value?.defaultEngine?.substringAfterLast('.') ?: "Android 系统 TTS",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    VoicePickerRow(
                        current = voice,
                        voices = voices.map { it.name },
                        enabled = ready && voices.isNotEmpty(),
                        onChange = { voice = it; save() },
                    )
                    if (!ready) {
                        Text(
                            "正在初始化本机语音引擎…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    } else if (voices.isEmpty()) {
                        Text(
                            "本机没有可用的语音，请在系统设置中安装或启用语音合成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("语速", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(
                                "%.2f".format(rate) + (if (rate == 1.0f) " · 正常" else ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        EmberSlider(
                            value = rate,
                            onValueChange = { rate = it; save() },
                            valueRange = 0.5f..2.0f,
                            steps = 29,
                        )
                        Text(
                            "0.5 慢速 — 2.0 快速（Android 系统支持范围，1.0 为正常）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = ::playSample,
                        enabled = ready && voices.isNotEmpty() && enabled,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text("试听本机语音")
                    }
                }
            }

            // 在线 TTS 提供商（27 后端，对照官方 tts/settings.html 的 provider 选项 + TtsBackendRegistry 注册表）
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Column {
                    Text(
                        "在线 TTS 提供商",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    Text(
                        "对照官方 TTS 扩展 27 个后端；选择后聊天朗读将走该后端（系统 TTS 仅用于本页试听）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    ProviderPickerRow(
                        current = ttsProvider,
                        options = listOf("system" to "系统 TTS（Android 本机）") +
                            TtsBackendRegistry.all().map { it.id to it.displayName },
                        onChange = { newP ->
                            val backend = TtsBackendRegistry.get(newP)
                            val ep = backend?.defaultEndpoint?.ifBlank { ttsEndpoint } ?: ttsEndpoint
                            saveProvider(newP, ep, ttsApiKey, ttsModel)
                            // 切换后端时异步加载该后端 voice 列表
                            if (backend != null) {
                                voicesLoading = true
                                scope.launch {
                                    val vs = runCatching { backend.getVoices(context) }.getOrDefault(emptyList())
                                    externalVoices = vs.map { v -> v.id }
                                    voicesLoading = false
                                }
                            } else {
                                externalVoices = emptyList()
                            }
                        },
                    )
                    val backend = TtsBackendRegistry.get(ttsProvider)
                    if (backend != null) {
                        if (backend.defaultEndpoint.isNotBlank()) {
                            EmberTextField(
                                value = ttsEndpoint,
                                onValueChange = { saveProvider(ttsProvider, it, ttsApiKey, ttsModel) },
                                label = { Text("端点（默认 ${backend.defaultEndpoint}）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        if (backend.requiresApiKey) {
                            KeyRow(
                                value = ttsApiKey,
                                visible = keyVisible,
                                onVisibleChange = { keyVisible = it },
                                onValueChange = { saveProvider(ttsProvider, ttsEndpoint, it, ttsModel) },
                                label = "API Key",
                            )
                        }
                        EmberTextField(
                            value = ttsModel,
                            onValueChange = { saveProvider(ttsProvider, ttsEndpoint, ttsApiKey, it) },
                            label = { Text("模型 / 语音名（按后端要求）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        if (voicesLoading) {
                            Text(
                                "正在加载语音列表…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        } else if (externalVoices.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            VoicePickerRow(
                                current = voice,
                                voices = externalVoices,
                                enabled = true,
                                onChange = { voice = it; save() },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Column {
                    Text(
                        "朗读选项",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    Text(
                        "字段对齐官方 TTS 扩展；聊天自动朗读在 P3 接入，以下配置先持久化",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    ToggleRow("自动朗读回复", autoGeneration) { autoGeneration = it; save() }
                    ToggleRow("同时朗读用户消息", narrateUser) { narrateUser = it; save() }
                    ToggleRow("按段落朗读", narrateByParagraphs) { narrateByParagraphs = it; save() }
                    ToggleRow("跳过代码块", skipCodeblocks) { skipCodeblocks = it; save() }
                    ToggleRow("跳过提示标签（*动作*）", skipTags) { skipTags = it; save() }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    ) {
                        Text("朗读前正则过滤", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        EmberSwitch(checked = applyRegex, onCheckedChange = { applyRegex = it; save() })
                    }
                    if (applyRegex) {
                        EmberTextField(
                            value = regexPattern,
                            onValueChange = { regexPattern = it; save() },
                            label = { Text("正则（例：/[^\\u4e00-\\u9fa5a-zA-Z0-9\\s.,!?]/g）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
    }
}

@Composable
private fun VoicePickerRow(
    current: String,
    voices: List<String>,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = { expanded = true })
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text("语音", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            current.ifBlank { "跟随系统默认" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("跟随系统默认") },
                onClick = { onChange(""); expanded = false },
            )
            voices.forEach { v ->
                DropdownMenuItem(
                    text = { Text(v, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onChange(v); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        EmberSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ProviderPickerRow(
    current: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == current }?.second ?: current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { expanded = true })
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text("提供商", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onChange(id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun KeyRow(
    value: String,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    label: String,
) {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = { onVisibleChange(!visible) }) {
                Text(if (visible) "隐藏" else "显示")
            }
        }
        EmberTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None
                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
