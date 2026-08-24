package com.emberinn.app.ui.settings


import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.design.EmberTheme
import android.widget.Toast
import com.emberinn.app.ui.design.components.EmberSwitch
import com.emberinn.app.data.ComfyWorkflowStore
import com.emberinn.app.data.PromptTemplateStore
import com.emberinn.app.data.StyleStore
import com.emberinn.engine.prompt.ImageGenPromptEngine
import com.emberinn.app.data.GenerationPrefs
import com.emberinn.app.ui.components.EmberTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
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
    // 1:1 对照官方 stable-diffusion/settings.html L44-67 顺序
    DropdownOption("aimlapi", "AI/ML API"),
    DropdownOption("bfl", "BFL (Black Forest Labs)"),
    DropdownOption("chutes", "Chutes"),
    DropdownOption("workersai", "Cloudflare Workers AI"),
    DropdownOption("comfy", "ComfyUI（需 workflow）"),
    DropdownOption("drawthings", "DrawThings HTTP API (macOS)"),
    DropdownOption("electronhub", "Electron Hub"),
    DropdownOption("extras", "Extras API (deprecated)"),
    DropdownOption("falai", "FAL.AI"),
    DropdownOption("google", "Google AI"),
    DropdownOption("huggingface", "Hugging Face Inference"),
    DropdownOption("nanogpt", "NanoGPT"),
    DropdownOption("novel", "NovelAI Diffusion"),
    DropdownOption("openai", "OpenAI · gpt-image"),
    DropdownOption("openrouter", "OpenRouter"),
    DropdownOption("pollinations", "Pollinations"),
    DropdownOption("vlad", "SD.Next (vladmandic)"),
    DropdownOption("stability", "Stability AI"),
    DropdownOption("auto", "Stable Diffusion Web UI (AUTOMATIC1111)"),
    DropdownOption("sdcpp", "stable-diffusion.cpp server"),
    DropdownOption("horde", "Stable Horde"),
    DropdownOption("togetherai", "TogetherAI"),
    DropdownOption("xai", "xAI (Grok)"),
    DropdownOption("zai", "Z.AI"),
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
            Text("翻译 · 图像 · 向量", style = MaterialTheme.typography.titleSmall, color = EmberTheme.colors.accent)
            Text(
                "字段对齐官方扩展设置；翻译 / 图像执行层已接入，向量检索已 1:1 接线。",
                style = MaterialTheme.typography.bodySmall,
                color = EmberTheme.colors.inkMute,
                modifier = Modifier.padding(top = 4.dp),
            )
            TranslateCard()
            ImageCard()
            VectorCard()
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
    var comfyType by rememberSaveable { mutableStateOf(ServicesPrefs.comfyType(context)) }
    var promptPrefix by rememberSaveable { mutableStateOf(ServicesPrefs.imagePromptPrefix(context)) }
    var negativePrompt by rememberSaveable { mutableStateOf(ServicesPrefs.imageNegativePrompt(context)) }
    var sampler by rememberSaveable { mutableStateOf(ServicesPrefs.imageSampler(context)) }
    var scheduler by rememberSaveable { mutableStateOf(ServicesPrefs.imageScheduler(context)) }
    var seed by rememberSaveable { mutableStateOf(ServicesPrefs.imageSeed(context).toString()) }
    var scale by rememberSaveable { mutableStateOf(ServicesPrefs.imageScale(context).toString()) }
    var width by rememberSaveable { mutableStateOf(ServicesPrefs.imageWidth(context).toString()) }
    var height by rememberSaveable { mutableStateOf(ServicesPrefs.imageHeight(context).toString()) }
    var restoreFaces by rememberSaveable { mutableStateOf(ServicesPrefs.imageRestoreFaces(context)) }
    var clipSkip by rememberSaveable { mutableStateOf(ServicesPrefs.imageClipSkip(context).toString()) }
    var vae by rememberSaveable { mutableStateOf(ServicesPrefs.imageVae(context)) }
    var enableHr by rememberSaveable { mutableStateOf(ServicesPrefs.imageEnableHr(context)) }
    var hrUpscaler by rememberSaveable { mutableStateOf(ServicesPrefs.imageHrUpscaler(context)) }
    var hrScale by rememberSaveable { mutableStateOf(ServicesPrefs.imageHrScale(context).toString()) }
    var hrSecondPassSteps by rememberSaveable { mutableStateOf(ServicesPrefs.imageHrSecondPassSteps(context).toString()) }
    var denoising by rememberSaveable { mutableStateOf(ServicesPrefs.imageDenoisingStrength(context).toString()) }
    var adetailerFace by rememberSaveable { mutableStateOf(ServicesPrefs.imageADetailerFace(context)) }
    var refineMode by rememberSaveable { mutableStateOf(ServicesPrefs.imageRefineMode(context)) }
    var interactiveMode by rememberSaveable { mutableStateOf(ServicesPrefs.imageInteractiveMode(context)) }
    var multimodalCaptioning by rememberSaveable { mutableStateOf(ServicesPrefs.imageMultimodalCaptioning(context)) }
    var freeExtend by rememberSaveable { mutableStateOf(ServicesPrefs.imageFreeExtend(context)) }
    fun save() = ServicesPrefs.saveImage(context, source, url, model, steps)
    fun saveAdvanced() = ServicesPrefs.saveImageAdvanced(
        context,
        promptPrefix,
        negativePrompt,
        sampler,
        scheduler,
        seed.toLongOrNull() ?: -1L,
        scale.toDoubleOrNull() ?: 7.0,
        width.toIntOrNull() ?: 512,
        height.toIntOrNull() ?: 512,
        restoreFaces,
        clipSkip.toIntOrNull() ?: 1,
        vae,
        enableHr,
        hrUpscaler,
        hrScale.toDoubleOrNull() ?: 1.0,
        hrSecondPassSteps.toIntOrNull() ?: 0,
        denoising.toDoubleOrNull() ?: 0.7,
    )

    ServiceCard(title = "图像生成") {
        ServiceNote("官方 stable-diffusion 扩展核心参数（A1111/sdcpp 请求体 1:1）。来源 / 地址 / 模型 / 步数 + 采样器 / CFG / 尺寸 / HR 等。")
        MenuPicker("来源", labelOf(IMAGE_SOURCES, source), IMAGE_SOURCES) { source = it; save() }
        // 需 API Key 的来源（对照官方 settings.html 各 source 子区段）
        val needsApiKey = source in setOf(
            "novel", "huggingface", "horde", "aimlapi", "bfl", "falai",
            "workersai", "stability", "pollinations", "chutes", "electronhub",
            "nanogpt", "togetherai", "extras", "zai", "google", "openrouter",
        )
        if (needsApiKey) {
            KeyRow(
                value = apiKey,
                visible = keyVisible,
                onVisibleChange = { keyVisible = it },
                onValueChange = { apiKey = it; ServicesPrefs.saveImageApiKey(context, it) },
                label = "API Key",
            )
        }
        if (source == "auto" || source == "sdcpp" || source == "comfy" || source == "vlad" || source == "drawthings") {
            TextFieldRow("接口地址", url) { url = it; save() }
        }
        if (source == "novel" || source == "sdcpp" || source == "huggingface" || source == "comfy" ||
            source == "zai" || source == "openrouter" || source == "workersai" || source == "google" ||
            source == "falai" || source == "extras"
        ) {
            TextFieldRow("模型", model) { model = it; save() }
        }
        if (source == "comfy") {
            MenuPicker(
                "服务器类型",
                if (comfyType == "runpod_serverless") "RunPod Serverless" else "Standard",
                listOf(
                    DropdownOption("standard", "Standard Server"),
                    DropdownOption("runpod_serverless", "RunPod Serverless Endpoint"),
                ),
            ) { comfyType = it; ServicesPrefs.saveComfyType(context, it) }
            if (comfyType == "standard") {
                ComfyWorkflowSection()
            }
        }
        EmberTextField(
            value = promptPrefix,
            onValueChange = { promptPrefix = it; saveAdvanced() },
            label = { Text("提示词前缀（sd_prompt_prefix）") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ShellInput(
            value = negativePrompt,
            onValueChange = { negativePrompt = it; saveAdvanced() },
            label = "负向提示（sd_negative_prompt）",
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        StyleSection(
            prefix = promptPrefix,
            negative = negativePrompt,
            onApply = { prefix, neg ->
                promptPrefix = prefix
                negativePrompt = neg
                saveAdvanced()
            },
        )
        ShellInput(
            value = steps.toString(),
            onValueChange = { steps = it.toIntOrNull() ?: 0; save() },
            label = "采样步数（steps）",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        TextFieldRow("采样器（sampler_name）", sampler) { sampler = it; saveAdvanced() }
        TextFieldRow("调度器（scheduler）", scheduler) { scheduler = it; saveAdvanced() }
        DecimalRow("CFG 强度（cfg_scale）", scale) { scale = it; saveAdvanced() }
        TextFieldRow("种子（seed，-1=随机）", seed) { seed = it; saveAdvanced() }
        TextFieldRow("宽度（width）", width) { width = it; saveAdvanced() }
        TextFieldRow("高度（height）", height) { height = it; saveAdvanced() }
        TextButton(
            onClick = {
                val w = width
                width = height
                height = w
                saveAdvanced()
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text("交换宽高（swap）") }
        ToggleRow("恢复人脸（restore_faces）", restoreFaces) { restoreFaces = it; saveAdvanced() }
        ToggleRow("ADetailer（人脸）", adetailerFace) { adetailerFace = it; ServicesPrefs.saveImageADetailerFace(context, it) }
        ToggleRow("生成前编辑提示词（refine）", refineMode) {
            refineMode = it; ServicesPrefs.saveImageModeToggle(context, "sd_refine_mode", it)
        }
        ToggleRow("交互模式（消息触发生图）", interactiveMode) {
            interactiveMode = it; ServicesPrefs.saveImageModeToggle(context, "sd_interactive_mode", it)
        }
        ToggleRow("头像多模态提示（multimodal）", multimodalCaptioning) {
            multimodalCaptioning = it; ServicesPrefs.saveImageModeToggle(context, "sd_multimodal_captioning", it)
        }
        ToggleRow("自由模式 LLM 扩写（free_extend）", freeExtend) {
            freeExtend = it; ServicesPrefs.saveImageModeToggle(context, "sd_free_extend", it)
        }
        PromptTemplatesSection()
        TextFieldRow("CLIP skip", clipSkip) { clipSkip = it; saveAdvanced() }
        TextFieldRow("VAE（留空=默认）", vae) { vae = it; saveAdvanced() }
        ToggleRow("高清修复（enable_hr）", enableHr) { enableHr = it; saveAdvanced() }
        if (enableHr) {
            TextFieldRow("HR 放大模型（hr_upscaler）", hrUpscaler) { hrUpscaler = it; saveAdvanced() }
            DecimalRow("HR 放大倍数（hr_scale）", hrScale) { hrScale = it; saveAdvanced() }
            NumberRow("HR 二次步数（hr_second_pass_steps）", hrSecondPassSteps) { hrSecondPassSteps = it; saveAdvanced() }
            DecimalRow("去噪强度（denoising_strength）", denoising) { denoising = it; saveAdvanced() }
        }
    }
}

/**
 * ComfyUI workflow 管理（对齐官方 stable-diffusion 扩展 comfyWorkflowEditor）：
 * 活动 workflow 选择 + 新建 / 重命名 / 删除 / 编辑（JSON 编辑器 + 占位符提示）。
 * 数据在 [ComfyWorkflowStore]（filesDir/comfy-workflows 目录，*.json 文件），含内嵌官方默认；标准与 RunPod 共用活动 workflow。
 */
@Composable
private fun ComfyWorkflowSection() {
    val context = LocalContext.current
    val store = remember { ComfyWorkflowStore(context) }
    var workflows by remember { mutableStateOf(store.workflows()) }
    var active by remember { mutableStateOf(store.active()) }
    var selectorOpen by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var draftJson by remember { mutableStateOf("") }
    var newOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }

    fun refresh() {
        workflows = store.workflows()
        active = store.active()
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box {
                FilterChip(selected = false, onClick = { selectorOpen = true }, label = { Text("Workflow：$active", maxLines = 1) })
                DropdownMenu(expanded = selectorOpen, onDismissRequest = { selectorOpen = false }) {
                    workflows.forEach { w ->
                        DropdownMenuItem(text = { Text(w) }, onClick = {
                            selectorOpen = false
                            store.setActive(w)
                            active = w
                        })
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                draftJson = store.read(active) ?: ""
                editorOpen = true
            }) { Text("编辑") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { draftName = ""; newOpen = true }) { Text("新建") }
            TextButton(onClick = { draftName = active; renameOpen = true }) { Text("重命名") }
            if (workflows.size > 1) {
                TextButton(onClick = { deleteOpen = true }) { Text("删除") }
            }
        }
        Text(
            "占位符：%prompt% / %negative_prompt% / %model% / %seed% / %steps% / %width% / %height% / %sampler% / %scheduler% / %scale% / %denoise% / %clip_skip% / %vae%",
            style = MaterialTheme.typography.bodySmall,
            color = EmberTheme.colors.inkMute,
            modifier = Modifier.padding(top = 2.dp),
        )
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = { editorOpen = false },
            title = { Text("编辑 Workflow：$active") },
            text = {
                Column {
                    ShellInput(
                        value = draftJson,
                        onValueChange = { draftJson = it },
                        label = "workflow JSON（API 格式，含占位符）",
                        minLines = 8,
                        maxLines = 16,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "占位符：%prompt% / %negative_prompt% / %model% / %seed% / %steps% / %width% / %height% / %sampler% / %scheduler% / %scale% / %denoise% / %clip_skip% / %vae%",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmberTheme.colors.inkMute,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draftJson.trim().isNotEmpty()) {
                        store.write(active, draftJson)
                        refresh()
                    } else {
                        Toast.makeText(context, "workflow JSON 不能为空", Toast.LENGTH_SHORT).show()
                    }
                    editorOpen = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editorOpen = false }) { Text("取消") } },
        )
    }

    if (newOpen) {
        AlertDialog(
            onDismissRequest = { newOpen = false },
            title = { Text("新建 Workflow") },
            text = {
                ShellInput(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = "workflow 名（自动补 .json）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = draftName.trim()
                    if (name.isNotEmpty()) {
                        val full = if (name.lowercase().endsWith(".json")) name else "$name.json"
                        store.write(full, ComfyWorkflowStore.DEFAULT_WORKFLOW)
                        store.setActive(full)
                        refresh()
                    } else {
                        Toast.makeText(context, "workflow 名不能为空", Toast.LENGTH_SHORT).show()
                    }
                    newOpen = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { newOpen = false }) { Text("取消") } },
        )
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("重命名 Workflow") },
            text = {
                ShellInput(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = "新名字（自动补 .json）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!store.rename(active, draftName)) {
                        Toast.makeText(context, "重命名失败：名字为空/重复/非法", Toast.LENGTH_SHORT).show()
                    } else {
                        refresh()
                    }
                    renameOpen = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("取消") } },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("删除 Workflow：$active？") },
            text = { Text("删除后不可恢复。至少保留 1 个 workflow。") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(active)
                    refresh()
                    deleteOpen = false
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("取消") } },
        )
    }
}

/**
 * 图像生成样式库（对齐官方 stable-diffusion 扩展 onStyleSelect/onSaveStyleClick/
 * onRenameStyleClick/onDeleteStyleClick）：选样式 → 应用到提示词前缀/负向；把当前前缀/负向存为样式。
 * 数据在 [StyleStore]（ember_services.sd_styles JSON 数组 + sd_style 活动名）。
 */
@Composable
private fun StyleSection(
    prefix: String,
    negative: String,
    onApply: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { StyleStore(context) }
    var styles by remember { mutableStateOf(store.styles()) }
    var active by remember { mutableStateOf(store.active()) }
    var selectorOpen by remember { mutableStateOf(false) }
    var saveOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }

    fun refresh() {
        styles = store.styles()
        active = store.active()
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box {
                FilterChip(
                    selected = false,
                    onClick = { selectorOpen = true },
                    label = { Text(if (active.isBlank()) "样式库（未选择）" else "样式：$active", maxLines = 1) },
                )
                DropdownMenu(expanded = selectorOpen, onDismissRequest = { selectorOpen = false }) {
                    styles.forEach { s ->
                        DropdownMenuItem(text = { Text(s.name) }, onClick = {
                            selectorOpen = false
                            store.setActive(s.name)
                            active = s.name
                            onApply(s.prefix, s.negative)
                        })
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { draftName = ""; saveOpen = true }) { Text("保存当前") }
            if (active.isNotBlank()) {
                TextButton(onClick = { draftName = active; renameOpen = true }) { Text("重命名") }
                TextButton(onClick = { deleteOpen = true }) { Text("删除") }
            }
        }
        Text(
            "选中样式会应用到上方提示词前缀/负向（官方 onStyleSelect）；「保存当前」用当前前缀/负向新建或覆盖同名样式。",
            style = MaterialTheme.typography.bodySmall,
            color = EmberTheme.colors.inkMute,
            modifier = Modifier.padding(top = 2.dp),
        )
    }

    if (saveOpen) {
        AlertDialog(
            onDismissRequest = { saveOpen = false },
            title = { Text("保存样式") },
            text = {
                Column {
                    Text("将用当前「提示词前缀 / 负向提示」保存为样式：", style = MaterialTheme.typography.bodySmall)
                    ShellInput(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = "样式名",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draftName.trim().isNotEmpty()) {
                        store.save(draftName.trim(), prefix, negative)
                        refresh()
                    } else {
                        Toast.makeText(context, "样式名不能为空", Toast.LENGTH_SHORT).show()
                    }
                    saveOpen = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { saveOpen = false }) { Text("取消") } },
        )
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("重命名样式") },
            text = {
                ShellInput(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = "新名字",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!store.rename(active, draftName)) {
                        Toast.makeText(context, "重命名失败：名字为空/重复", Toast.LENGTH_SHORT).show()
                    } else {
                        refresh()
                    }
                    renameOpen = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("取消") } },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("删除样式：$active？") },
            text = { Text("删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(active)
                    refresh()
                    deleteOpen = false
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("取消") } },
        )
    }
}

/**
 * 图像生成 prompt templates（对齐官方 stable-diffusion 扩展 addPromptTemplates）：
 * 13 个 generationMode 模板，按模式号排序渲染；可编辑写回 sd_prompts，每行「恢复默认」。
 * 数据在 [PromptTemplateStore]（合并引擎默认 [ImageGenPromptEngine.DEFAULT_PROMPT_TEMPLATES]）。
 */
@Composable
private fun PromptTemplatesSection() {
    val context = LocalContext.current
    val store = remember { PromptTemplateStore(context) }
    var templates by remember { mutableStateOf(store.templates()) }
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        ) {
            Text("图像提示词模板（${templates.size}）", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text(if (expanded) "收起 ▾" else "展开 ▴", color = EmberTheme.colors.accent)
        }
        Text(
            "官方 13 个 generationMode 预设提示词（getQuietPrompt 使用），可编辑；点「恢复默认」还原官方原文。",
            style = MaterialTheme.typography.bodySmall,
            color = EmberTheme.colors.inkMute,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (expanded) {
            // 官方 addPromptTemplates 按 Number(key) 升序排序
            val sorted = templates.entries.sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            for ((key, text) in sorted) {
                val label = ImageGenPromptEngine.MODE_LABELS[key] ?: key
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        store.restore(key)
                        templates = store.templates()
                    }) { Text("恢复默认") }
                }
                ShellInput(
                    value = text,
                    onValueChange = {
                        store.set(key, it)
                        templates = store.templates()
                    },
                    label = "模式 $key 模板",
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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
                color = EmberTheme.colors.inkMute,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }

}

@Composable
private fun ServiceCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EmberTheme.colors.surface),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = EmberTheme.colors.accent,
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
        color = EmberTheme.colors.inkMute,
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
        EmberSwitch(checked = checked, onChange = onChange)
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
            color = EmberTheme.colors.inkMute,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text("›", style = MaterialTheme.typography.titleMedium, color = EmberTheme.colors.inkMute)
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
        ShellInput(
            value = sendIfEmpty,
            onValueChange = { sendIfEmpty = it; GenerationPrefs.saveSendIfEmpty(context, it) },
            label = "空输入时发送（send_if_empty）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}


