package com.emberinn.app.ui.settings


import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.data.GenerationPrefs
import com.emberinn.app.ui.components.EmberTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class DropdownOption(val value: String, val label: String)

// 官方 translate/index.html 的提供商与自动翻译模式
private val TRANSLATE_PROVIDERS = listOf(
    DropdownOption("libre", "LibreTranslate"),
    DropdownOption("google", "Google"),
    DropdownOption("lingva", "Lingva"),
    DropdownOption("deepl", "DeepL API"),
    DropdownOption("deeplx", "DeepLX"),
    DropdownOption("bing", "Bing"),
    DropdownOption("oneringtranslator", "OneRingTranslator"),
    DropdownOption("yandex", "Yandex"),
)
private val TRANSLATE_MODES = listOf(
    DropdownOption("none", "不自动翻译"),
    DropdownOption("responses", "翻译回复"),
    DropdownOption("inputs", "翻译输入"),
    DropdownOption("both", "两者都翻译"),
)
private val TARGET_LANGUAGES = listOf(
    DropdownOption("zh", "中文"), DropdownOption("en", "English"), DropdownOption("ja", "日本語"),
    DropdownOption("ko", "한국어"), DropdownOption("fr", "Français"), DropdownOption("de", "Deutsch"),
    DropdownOption("es", "Español"), DropdownOption("pt", "Português"), DropdownOption("it", "Italiano"),
    DropdownOption("ru", "Русский"), DropdownOption("ar", "العربية"), DropdownOption("nl", "Nederlands"),
    DropdownOption("pl", "Polski"), DropdownOption("tr", "Türkçe"), DropdownOption("uk", "Українська"),
)

// 官方 stable-diffusion/index.js 的 sources（取主流自托管 / 云来源）
private val IMAGE_SOURCES = listOf(
    DropdownOption("auto", "AUTOMATIC1111"),
    DropdownOption("sdcpp", "SDCPP（本地 /sdapi/v1/txt2img）"),
    DropdownOption("novel", "NovelAI"),
    DropdownOption("openai", "OpenAI · gpt-image"),
    DropdownOption("huggingface", "Hugging Face Inference"),
    DropdownOption("horde", "Stable Horde"),
    DropdownOption("comfy", "ComfyUI（需 workflow）"),
)

private val VECTOR_PROVIDERS = listOf(
    DropdownOption("openai", "OpenAI 兼容嵌入"),
    DropdownOption("local", "本地离线（BagOfGram）"),
)

/** 服务页：翻译 / 图像生成 / 向量检索配置，字段对齐官方扩展；执行层为 P3 引擎服务。 */
@Composable
fun ServicesScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "服务", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("翻译 · 图像 · 向量", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "字段对齐官方扩展设置；翻译 / 图像执行层已接入，向量检索已 1:1 接线。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TranslateCard()
            ImageCard()
            VectorCard()
            ReasoningCard()
            SendCard()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TranslateCard() {
    val context = LocalContext.current
    var provider by rememberSaveable { mutableStateOf(ServicesPrefs.translateProvider(context)) }
    var autoMode by rememberSaveable { mutableStateOf(ServicesPrefs.translateAutoMode(context)) }
    var target by rememberSaveable { mutableStateOf(ServicesPrefs.translateTargetLanguage(context)) }
    var apiKey by rememberSaveable { mutableStateOf(ServicesPrefs.translateApiKey(context)) }
    var url by rememberSaveable { mutableStateOf(ServicesPrefs.translateUrl(context)) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    fun save() = ServicesPrefs.saveTranslate(context, provider, autoMode, target, apiKey)
    fun saveUrl(v: String) { url = v; ServicesPrefs.saveTranslateUrl(context, v) }

    ServiceCard(title = "翻译") {
        ServiceNote("官方 translate 扩展：自动模式 / 提供商 / 目标语言 / API Key。聊天长按消息可翻译。")
        MenuPicker("自动翻译", labelOf(TRANSLATE_MODES, autoMode), TRANSLATE_MODES) { autoMode = it; save() }
        MenuPicker("提供商", labelOf(TRANSLATE_PROVIDERS, provider), TRANSLATE_PROVIDERS) { provider = it; save() }
        MenuPicker("目标语言", labelOf(TARGET_LANGUAGES, target), TARGET_LANGUAGES) { target = it; save() }
        KeyRow(
            value = apiKey,
            visible = keyVisible,
            onVisibleChange = { keyVisible = it },
            onValueChange = { apiKey = it; save() },
            label = "API Key（按提供商需要）",
        )
        TextFieldRow("接口地址（可空：Libre 官方/DeepL free 默认）", url) { saveUrl(it) }
    }
}

@Composable
private fun ImageCard() {
    val context = LocalContext.current
    var source by rememberSaveable { mutableStateOf(ServicesPrefs.imageSource(context)) }
    var url by rememberSaveable { mutableStateOf(ServicesPrefs.imageUrl(context)) }
    var model by rememberSaveable { mutableStateOf(ServicesPrefs.imageModel(context)) }
    var steps by rememberSaveable { mutableStateOf(ServicesPrefs.imageSteps(context)) }
    var apiKey by rememberSaveable { mutableStateOf(ServicesPrefs.imageApiKey(context)) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var comfyWorkflow by rememberSaveable { mutableStateOf(ServicesPrefs.comfyWorkflow(context)) }
    fun save() = ServicesPrefs.saveImage(context, source, url, model, steps)

    ServiceCard(title = "图像生成") {
        ServiceNote("官方 stable-diffusion 扩展：来源 / 接口地址 / 模型 / 采样步数。快捷工具盘可生成图像。")
        MenuPicker("来源", labelOf(IMAGE_SOURCES, source), IMAGE_SOURCES) { source = it; save() }
        if (source == "novel" || source == "huggingface" || source == "horde") {
            KeyRow(
                value = apiKey,
                visible = keyVisible,
                onVisibleChange = { keyVisible = it },
                onValueChange = { apiKey = it; ServicesPrefs.saveImageApiKey(context, it) },
                label = "API Key",
            )
        }
        if (source == "auto" || source == "sdcpp" || source == "comfy") {
            TextFieldRow("接口地址", url) { url = it; save() }
        }
        if (source == "novel" || source == "sdcpp" || source == "huggingface" || source == "comfy") {
            TextFieldRow("模型", model) { model = it; save() }
        }
        if (source == "comfy") {
            EmberTextField(
                value = comfyWorkflow,
                onValueChange = { comfyWorkflow = it; ServicesPrefs.saveComfyWorkflow(context, it) },
                label = { Text("ComfyUI workflow JSON（含 %prompt%/%model%/%steps%/%width%/%height% 等占位符）") },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        EmberTextField(
            value = steps.toString(),
            onValueChange = { steps = it.toIntOrNull() ?: 0; save() },
            label = { Text("采样步数") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun VectorCard() {
    val context = LocalContext.current
    var provider by rememberSaveable { mutableStateOf(ServicesPrefs.vectorProvider(context)) }
    var url by rememberSaveable { mutableStateOf(ServicesPrefs.vectorUrl(context)) }
    var apiKey by rememberSaveable { mutableStateOf(ServicesPrefs.vectorApiKey(context)) }
    var model by rememberSaveable { mutableStateOf(ServicesPrefs.vectorModel(context)) }
    var enabled by rememberSaveable { mutableStateOf(ServicesPrefs.vectorEnabled(context)) }
    var enabledChats by rememberSaveable { mutableStateOf(ServicesPrefs.vectorEnabledChats(context)) }
    var enabledFiles by rememberSaveable { mutableStateOf(ServicesPrefs.vectorEnabledFiles(context)) }
    var query by rememberSaveable { mutableStateOf(ServicesPrefs.vectorQuery(context).toString()) }
    var insert by rememberSaveable { mutableStateOf(ServicesPrefs.vectorInsert(context).toString()) }
    var protect by rememberSaveable { mutableStateOf(ServicesPrefs.vectorProtect(context).toString()) }
    var threshold by rememberSaveable { mutableStateOf(ServicesPrefs.vectorThreshold(context).toString()) }
    var sizeThresholdDb by rememberSaveable { mutableStateOf(ServicesPrefs.vectorSizeThresholdDb(context).toString()) }
    var chunkCountDb by rememberSaveable { mutableStateOf(ServicesPrefs.vectorChunkCountDb(context).toString()) }
    var overlapPercentDb by rememberSaveable { mutableStateOf(ServicesPrefs.vectorOverlapPercentDb(context).toString()) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    fun saveAdvanced() = ServicesPrefs.saveVectorAdvanced(
        context,
        sizeThresholdDb.toIntOrNull()?.coerceAtLeast(1) ?: 5,
        chunkCountDb.toIntOrNull()?.coerceAtLeast(1) ?: 5,
        overlapPercentDb.toIntOrNull()?.coerceIn(0, 100) ?: 0,
    )
    fun save() = ServicesPrefs.saveVector(
        context,
        provider,
        url,
        apiKey,
        model,
        enabled,
        enabledChats,
        enabledFiles,
        query.toIntOrNull()?.coerceAtLeast(1) ?: 2,
        insert.toIntOrNull()?.coerceAtLeast(1) ?: 3,
        protect.toIntOrNull()?.coerceAtLeast(0) ?: 5,
        threshold.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.25,
    )

    ServiceCard(title = "向量检索（RAG）") {
        ServiceNote("引擎（聊天重排 / 世界书强制激活 / 文件分块检索）已 1:1 接线；数据银行文件在聊天 ⋮ 菜单管理。")
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            VECTOR_PROVIDERS.forEach { pv ->
                FilterChip(
                    selected = provider == pv.value,
                    onClick = { provider = pv.value; save() },
                    label = { Text(pv.label) },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        ToggleRow("启用向量检索（RAG）", enabled) { enabled = it; save() }
        ToggleRow("聊天历史重排（向量记忆）", enabledChats) { enabledChats = it; save() }
        ToggleRow("文件 / 数据银行检索", enabledFiles) { enabledFiles = it; save() }
        NumberRow("最近消息数（query）", query) { query = it; save() }
        NumberRow("插入条数（insert）", insert) { insert = it; save() }
        NumberRow("保护最近条数（protect）", protect) { protect = it; save() }
        DecimalRow("相似度阈值（0–1）", threshold) { threshold = it; save() }
        NumberRow("文件入库阈值 KB（sizeThresholdDb）", sizeThresholdDb) { sizeThresholdDb = it; saveAdvanced() }
        NumberRow("每文件检索块数（chunkCountDb）", chunkCountDb) { chunkCountDb = it; saveAdvanced() }
        NumberRow("块重叠 %（overlapPercentDb）", overlapPercentDb) { overlapPercentDb = it; saveAdvanced() }
        if (provider == "openai") {
            TextFieldRow("接口地址", url) { url = it; save() }
            KeyRow(
                value = apiKey,
                visible = keyVisible,
                onVisibleChange = { keyVisible = it },
                onValueChange = { apiKey = it; save() },
                label = "API Key",
            )
            TextFieldRow("嵌入模型", model) { model = it; save() }
        } else {
            Text(
                "本地离线嵌入无需联网，适合隐私场景；效果弱于云端嵌入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }

}

@Composable
private fun ServiceCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            content()
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ServiceNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        EmberSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberRow(label: String, value: String, onValueChange: (String) -> Unit) {
    EmberTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun DecimalRow(label: String, value: String, onValueChange: (String) -> Unit) {
    EmberTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** 点击行 + DropdownMenu 选择器。 */
@Composable
private fun MenuPicker(label: String, value: String, options: List<DropdownOption>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { onSelect(option.value); expanded = false },
                )
            }
        }
    }
}

private fun labelOf(options: List<DropdownOption>, value: String): String =
    options.firstOrNull { it.value == value }?.label ?: value

@Composable
private fun TextFieldRow(label: String, value: String, onValueChange: (String) -> Unit) {
    EmberTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun KeyRow(
    value: String,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    label: String,
) {
    EmberTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { onVisibleChange(!visible) }) {
                Text(if (visible) "隐藏" else "显示")
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** 发送行为（官方 oai_settings.send_if_empty：最后一条 AI 且输入为空时发送该文本续聊）。 */
@Composable
private fun SendCard() {
    val context = LocalContext.current
    var sendIfEmpty by rememberSaveable { mutableStateOf(GenerationPrefs.sendIfEmpty(context)) }
    ServiceCard(title = "发送") {
        ServiceNote("官方 send_if_empty：当最后一条是 AI 回复且输入框为空时，用这段文本作为用户消息续聊；留空 = 关闭。")
        EmberTextField(
            value = sendIfEmpty,
            onValueChange = { sendIfEmpty = it; GenerationPrefs.saveSendIfEmpty(context, it) },
            label = { Text("空输入时发送（send_if_empty）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** 思考过程入提示词（官方 power_user.reasoning.add_to_prompts，默认关；prefix/suffix/separator 用官方默认）。 */
@Composable
private fun ReasoningCard() {
    val context = LocalContext.current
    var enabled by rememberSaveable { mutableStateOf(GenerationPrefs.reasoningToPrompts(context)) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("把思考过程加入提示词", style = MaterialTheme.typography.titleSmall)
                Text(
                    "对齐官方 power_user.reasoning.add_to_prompts（默认关）。开启后历史消息的思考会按 <think>…</think> 注入提示词，continue 的最后一条前缀不受开关限制（官方语义）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            EmberSwitch(
                checked = enabled,
                onCheckedChange = { on ->
                    enabled = on
                    GenerationPrefs.saveReasoningToPrompts(context, on)
                },
            )
        }
    }
}
