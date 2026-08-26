package com.emberinn.app.ui.settings


import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.ShellActionButton
import com.emberinn.app.data.TtsBackendRegistry
import com.emberinn.app.data.TtsReader
import com.emberinn.app.ui.design.components.EmberSwitch
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
import androidx.compose.ui.unit.sp
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
    var narrateQuotedOnly by rememberSaveable { mutableStateOf(VoicePrefs.narrateQuotedOnly(context)) }
    var narrateDialoguesOnly by rememberSaveable { mutableStateOf(VoicePrefs.narrateDialoguesOnly(context)) }
    var narrateTranslatedOnly by rememberSaveable { mutableStateOf(VoicePrefs.narrateTranslatedOnly(context)) }
    var skipCodeblocks by rememberSaveable { mutableStateOf(VoicePrefs.skipCodeblocks(context)) }
    var skipTags by rememberSaveable { mutableStateOf(VoicePrefs.skipTags(context)) }
    var passAsterisks by rememberSaveable { mutableStateOf(VoicePrefs.passAsterisks(context)) }
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
        VoicePrefs.periodicAutoGeneration(context), narrateByParagraphs,
        narrateQuotedOnly, narrateDialoguesOnly, narrateTranslatedOnly,
        skipCodeblocks, skipTags, passAsterisks,
        VoicePrefs.multiVoiceEnabled(context), applyRegex, regexPattern,
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
            Text("语音朗读（TTS）", style = MaterialTheme.typography.titleSmall, color = EmberTheme.colors.accent)
            Text(
                "对齐官方 tts 扩展设置（settings.html）；官方 1.18 无语音输入（STT）。",
                style = MaterialTheme.typography.bodySmall,
                color = EmberTheme.colors.inkMute,
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用朗读", color = EmberTheme.colors.ink, fontSize = EmberTheme.typo.subhead.fontSize)
                    Text(
                        "开启后按下方自动朗读选项工作；本页试听亦需此项",
                        fontSize = EmberTheme.typo.caption.fontSize,
                        color = EmberTheme.colors.inkMute,
                    )
                }
                EmberSwitch(checked = enabled, onChange = { enabled = it; save() })
            }

GroupLabel("引擎与语音")

                    Text(
                        "引擎与语音",
                        style = MaterialTheme.typography.titleSmall,
                        color = EmberTheme.colors.accent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                                        Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    ) {
                        Text("引擎", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            tts.value?.defaultEngine?.substringAfterLast('.') ?: "Android 系统 TTS",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmberTheme.colors.inkMute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                            color = EmberTheme.colors.lineStrong,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    } else if (voices.isEmpty()) {
                        Text(
                            "本机没有可用的语音，请在系统设置中安装或启用语音合成",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmberTheme.colors.danger,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("语速", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(
                                "%.2f".format(rate) + (if (rate == 1.0f) " · 正常" else ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = EmberTheme.colors.inkMute,
                            )
                        }
                        // 官方 playback_rate：min=0 max=3 step=0.05（settings.html:89）
                        EmberSlider(
                            value = rate,
                            onValueChange = { rate = it; save() },
                            valueRange = 0f..3f,
                            steps = 59,
                        )
                        Text(
                            "Audio Playback Speed：0 — 3，步长 0.05，默认 1 为正常",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmberTheme.colors.inkMute,
                        )
                    }
                    ShellActionButton(
                        label = "试听本机语音",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        enabled = ready && voices.isNotEmpty() && enabled,
                    ) { playSample() }
                


            // 在线 TTS 提供商（27 后端，对照官方 tts/settings.html 的 provider 选项 + TtsBackendRegistry 注册表）
GroupLabel("在线 TTS 提供商")

                    Text(
                        "在线 TTS 提供商",
                        style = MaterialTheme.typography.titleSmall,
                        color = EmberTheme.colors.accent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    Text(
                        "对照官方 TTS 扩展 27 个后端；选择后聊天朗读将走该后端（系统 TTS 仅用于本页试听）",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmberTheme.colors.inkMute,
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
                            ShellInput(
                                value = ttsEndpoint,
                                onValueChange = { saveProvider(ttsProvider, it, ttsApiKey, ttsModel) },
                                label = "端点（默认 ${backend.defaultEndpoint}）",
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
                        ShellInput(
                            value = ttsModel,
                            onValueChange = { saveProvider(ttsProvider, ttsEndpoint, ttsApiKey, it) },
                            label = "模型 / 语音名（按后端要求）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        if (voicesLoading) {
                            Text(
                                "正在加载语音列表…",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmberTheme.colors.lineStrong,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        } else if (externalVoices.isNotEmpty()) {
                                                        VoicePickerRow(
                                current = voice,
                                voices = externalVoices,
                                enabled = true,
                                onChange = { voice = it; save() },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                


GroupLabel("朗读选项")

                    Text(
                        "朗读选项",
                        style = MaterialTheme.typography.titleSmall,
                        color = EmberTheme.colors.accent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    Text(
                        "开关项与顺序对齐官方 tts 扩展 settings.html 的复选框列表",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmberTheme.colors.inkMute,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    // 官方复选框顺序（settings.html:18-66）：enabled 在页首已单独成行
                    ToggleRow("同时朗读用户消息（Narrate user messages）", narrateUser) { narrateUser = it; save() }
                    ToggleRow("自动朗读回复（Auto Generation）", autoGeneration) { autoGeneration = it; save() }
                    ToggleRow("按段落朗读·非流式时（Narrate by paragraphs）", narrateByParagraphs) { narrateByParagraphs = it; save() }
                    ToggleRow("仅朗读引号内文本", narrateQuotedOnly) { narrateQuotedOnly = it; save() }
                    ToggleRow("忽略星号内文本（*含引号*）", narrateDialoguesOnly) { narrateDialoguesOnly = it; save() }
                    ToggleRow("仅朗读译文（无译文跳过该条）", narrateTranslatedOnly) { narrateTranslatedOnly = it; save() }
                    ToggleRow("跳过代码块", skipCodeblocks) { skipCodeblocks = it; save() }
                    ToggleRow("跳过 <tagged> 标签块", skipTags) { skipTags = it; save() }
                    ToggleRow("保留星号传给引擎", passAsterisks) { passAsterisks = it; save() }
                                        Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    ) {
                        Text("朗读前正则过滤", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        EmberSwitch(checked = applyRegex, onChange = { applyRegex = it; save() })
                    }
                    if (applyRegex) {
                        ShellInput(
                            value = regexPattern,
                            onValueChange = { regexPattern = it; save() },
                            label = "正则（例：/[^\\u4e00-\\u9fa5a-zA-Z0-9\\s.,!?]/g）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                

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
            color = EmberTheme.colors.inkMute,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text("›", style = MaterialTheme.typography.titleMedium, color = EmberTheme.colors.inkMute)
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
        Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        EmberSwitch(checked = checked, onChange = onChange)
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
            color = EmberTheme.colors.inkMute,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text("›", style = MaterialTheme.typography.titleMedium, color = EmberTheme.colors.inkMute)
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = { onVisibleChange(!visible) }) {
                Text(if (visible) "隐藏" else "显示")
            }
        }
        ShellInput(value = value, onValueChange = onValueChange, label = label, singleLine = true, visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None
                else androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    }
}
