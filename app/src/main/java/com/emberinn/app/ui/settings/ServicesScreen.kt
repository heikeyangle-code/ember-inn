package com.emberinn.app.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
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
    DropdownOption("comfy", "ComfyUI"),
    DropdownOption("sdcpp", "SDCPP"),
    DropdownOption("drawthings", "Draw Things"),
    DropdownOption("horde", "Stable Horde"),
    DropdownOption("novel", "NovelAI"),
    DropdownOption("openai", "OpenAI · gpt-image"),
    DropdownOption("huggingface", "Hugging Face"),
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
                "字段对齐官方扩展设置；翻译 / 图像请求执行在 P3 引擎层接入，本页先持久化配置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TranslateCard()
            ImageCard()
            VectorCard()
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
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    fun save() = ServicesPrefs.saveTranslate(context, provider, autoMode, target, apiKey)

    ServiceCard(title = "翻译") {
        ServiceNote("官方 translate 扩展：自动模式 / 提供商 / 目标语言 / API Key。翻译执行在 P3 接入。")
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
    }
}

@Composable
private fun ImageCard() {
    val context = LocalContext.current
    var source by rememberSaveable { mutableStateOf(ServicesPrefs.imageSource(context)) }
    var url by rememberSaveable { mutableStateOf(ServicesPrefs.imageUrl(context)) }
    var model by rememberSaveable { mutableStateOf(ServicesPrefs.imageModel(context)) }
    var steps by rememberSaveable { mutableStateOf(ServicesPrefs.imageSteps(context)) }
    fun save() = ServicesPrefs.saveImage(context, source, url, model, steps)

    ServiceCard(title = "图像生成") {
        ServiceNote("官方 stable-diffusion 扩展：来源 / 接口地址 / 模型 / 采样步数。图像生成执行在 P3 接入。")
        MenuPicker("来源", labelOf(IMAGE_SOURCES, source), IMAGE_SOURCES) { source = it; save() }
        TextFieldRow("接口地址", url) { url = it; save() }
        TextFieldRow("模型", model) { model = it; save() }
        OutlinedTextField(
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
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    fun save() = ServicesPrefs.saveVector(context, provider, url, apiKey, model)

    ServiceCard(title = "向量检索（RAG）") {
        ServiceNote("世界书 / 文件向量化引擎已就绪（官方 vectors 扩展 1:1）；聊天接线在 P3。选择嵌入来源：")
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            VECTOR_PROVIDERS.forEach { p ->
                FilterChip(
                    selected = provider == p.value,
                    onClick = { provider = p.value; save() },
                    label = { Text(p.label) },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
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
        shape = MaterialTheme.shapes.extraLarge,
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
    OutlinedTextField(
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
    OutlinedTextField(
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
