package com.emberinn.app.ui.settings


import com.emberinn.app.ui.design.components.ShellChip
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.design.EmberTheme
import android.widget.Toast
import com.emberinn.app.ui.design.components.EmberSwitch
import com.emberinn.app.data.ComfyWorkflowStore
import com.emberinn.app.data.ImageGenBackendsCloud
import com.emberinn.app.data.ImageGenClient
import com.emberinn.app.data.PromptTemplateStore
import com.emberinn.app.data.StyleStore
import com.emberinn.engine.prompt.ImageGenPromptEngine
import com.emberinn.engine.prompt.ImageGenRequestEngine
import com.emberinn.app.data.GenerationPrefs
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
// 官方 #deepl_api_endpoint（index.html:27-30）：仅 provider==deepl 时显示
private val DEEPL_ENDPOINTS = listOf(
    DropdownOption("free", "Free"),
    DropdownOption("pro", "Pro"),
)
// 官方 LOCAL_URL（index.js:150）：显示 URL 输入的提供商
private val LOCAL_URL_PROVIDERS = listOf("libre", "oneringtranslator", "deeplx", "lingva")
// 官方 translate/index.js languageCodes 全 105 项（声明顺序=官方下拉顺序，label/value 逐字）
private val TARGET_LANGUAGES = listOf(
    DropdownOption("af", "Afrikaans"),
    DropdownOption("sq", "Albanian"),
    DropdownOption("am", "Amharic"),
    DropdownOption("ar", "Arabic"),
    DropdownOption("hy", "Armenian"),
    DropdownOption("az", "Azerbaijani"),
    DropdownOption("eu", "Basque"),
    DropdownOption("be", "Belarusian"),
    DropdownOption("bn", "Bengali"),
    DropdownOption("bs", "Bosnian"),
    DropdownOption("bg", "Bulgarian"),
    DropdownOption("ca", "Catalan"),
    DropdownOption("ceb", "Cebuano"),
    DropdownOption("zh-CN", "Chinese (Simplified)"),
    DropdownOption("zh-TW", "Chinese (Traditional)"),
    DropdownOption("co", "Corsican"),
    DropdownOption("hr", "Croatian"),
    DropdownOption("cs", "Czech"),
    DropdownOption("da", "Danish"),
    DropdownOption("nl", "Dutch"),
    DropdownOption("en", "English"),
    DropdownOption("eo", "Esperanto"),
    DropdownOption("et", "Estonian"),
    DropdownOption("fi", "Finnish"),
    DropdownOption("fr", "French"),
    DropdownOption("fy", "Frisian"),
    DropdownOption("gl", "Galician"),
    DropdownOption("ka", "Georgian"),
    DropdownOption("de", "German"),
    DropdownOption("el", "Greek"),
    DropdownOption("gu", "Gujarati"),
    DropdownOption("ht", "Haitian Creole"),
    DropdownOption("ha", "Hausa"),
    DropdownOption("haw", "Hawaiian"),
    DropdownOption("iw", "Hebrew"),
    DropdownOption("hi", "Hindi"),
    DropdownOption("hmn", "Hmong"),
    DropdownOption("hu", "Hungarian"),
    DropdownOption("is", "Icelandic"),
    DropdownOption("ig", "Igbo"),
    DropdownOption("id", "Indonesian"),
    DropdownOption("ga", "Irish"),
    DropdownOption("it", "Italian"),
    DropdownOption("ja", "Japanese"),
    DropdownOption("jw", "Javanese"),
    DropdownOption("kn", "Kannada"),
    DropdownOption("kk", "Kazakh"),
    DropdownOption("km", "Khmer"),
    DropdownOption("ko", "Korean"),
    DropdownOption("ku", "Kurdish"),
    DropdownOption("ky", "Kyrgyz"),
    DropdownOption("lo", "Lao"),
    DropdownOption("la", "Latin"),
    DropdownOption("lv", "Latvian"),
    DropdownOption("lt", "Lithuanian"),
    DropdownOption("lb", "Luxembourgish"),
    DropdownOption("mk", "Macedonian"),
    DropdownOption("mg", "Malagasy"),
    DropdownOption("ms", "Malay"),
    DropdownOption("ml", "Malayalam"),
    DropdownOption("mt", "Maltese"),
    DropdownOption("mi", "Maori"),
    DropdownOption("mr", "Marathi"),
    DropdownOption("mn", "Mongolian"),
    DropdownOption("my", "Myanmar (Burmese)"),
    DropdownOption("ne", "Nepali"),
    DropdownOption("no", "Norwegian"),
    DropdownOption("ny", "Nyanja (Chichewa)"),
    DropdownOption("ps", "Pashto"),
    DropdownOption("fa", "Persian"),
    DropdownOption("pl", "Polish"),
    DropdownOption("pt-PT", "Portuguese (Portugal)"),
    DropdownOption("pt-BR", "Portuguese (Brazil)"),
    DropdownOption("pa", "Punjabi"),
    DropdownOption("ro", "Romanian"),
    DropdownOption("ru", "Russian"),
    DropdownOption("sm", "Samoan"),
    DropdownOption("gd", "Scots Gaelic"),
    DropdownOption("sr", "Serbian"),
    DropdownOption("st", "Sesotho"),
    DropdownOption("sn", "Shona"),
    DropdownOption("sd", "Sindhi"),
    DropdownOption("si", "Sinhala (Sinhalese)"),
    DropdownOption("sk", "Slovak"),
    DropdownOption("sl", "Slovenian"),
    DropdownOption("so", "Somali"),
    DropdownOption("es", "Spanish"),
    DropdownOption("su", "Sundanese"),
    DropdownOption("sw", "Swahili"),
    DropdownOption("sv", "Swedish"),
    DropdownOption("tl", "Tagalog (Filipino)"),
    DropdownOption("tg", "Tajik"),
    DropdownOption("ta", "Tamil"),
    DropdownOption("te", "Telugu"),
    DropdownOption("th", "Thai"),
    DropdownOption("tr", "Turkish"),
    DropdownOption("uk", "Ukrainian"),
    DropdownOption("ur", "Urdu"),
    DropdownOption("uz", "Uzbek"),
    DropdownOption("vi", "Vietnamese"),
    DropdownOption("cy", "Welsh"),
    DropdownOption("xh", "Xhosa"),
    DropdownOption("yi", "Yiddish"),
    DropdownOption("yo", "Yoruba"),
    DropdownOption("zu", "Zulu"),
)

/** 官方 languageCodes 值集合（translate/index.js），供 /translate target= 校验 */
internal val TRANSLATE_LANGUAGE_CODES = TARGET_LANGUAGES.map { it.value }

/** 官方 resolutionOptions 各项显示名（index.js L1062-L1093，translate 文案的直译/保留）。 */
private val RESOLUTION_LABELS = mapOf(
    "sd_res_512x512" to "512x512（1:1，图标/头像）",
    "sd_res_600x600" to "600x600（1:1，图标/头像）",
    "sd_res_512x768" to "512x768（2:3，竖版角色卡）",
    "sd_res_768x512" to "768x512（3:2，横版）",
    "sd_res_960x540" to "960x540（16:9，横版壁纸）",
    "sd_res_540x960" to "540x960（9:16，竖版壁纸）",
    "sd_res_1920x1088" to "1920x1088（16:9 1080p 横版壁纸）",
    "sd_res_1088x1920" to "1088x1920（9:16 1080p 竖版壁纸）",
    "sd_res_1280x720" to "1280x720（16:9 720p 横版壁纸）",
    "sd_res_720x1280" to "720x1280（9:16 720p 竖版壁纸）",
    "sd_res_1024x1024" to "1024x1024（1:1，SDXL）",
    "sd_res_1152x896" to "1152x896（9:7，SDXL）",
    "sd_res_896x1152" to "896x1152（7:9，SDXL）",
    "sd_res_1216x832" to "1216x832（19:13，SDXL）",
    "sd_res_832x1216" to "832x1216（13:19，SDXL）",
    "sd_res_1344x768" to "1344x768（4:3，SDXL）",
    "sd_res_768x1344" to "768x1344（3:4，SDXL）",
    "sd_res_1536x640" to "1536x640（24:10，SDXL）",
    "sd_res_640x1536" to "640x1536（10:24，SDXL）",
    "sd_res_1536x1024" to "1536x1024（3:2，ChatGPT）",
    "sd_res_1024x1536" to "1024x1536（2:3，ChatGPT）",
    "sd_res_1024x1792" to "1024x1792（4:7，DALL-E）",
    "sd_res_1792x1024" to "1792x1024（7:4，DALL-E）",
    "sd_res_1280x1280" to "1280x1280（1:1，Z.AI）",
    "sd_res_1568x1056" to "1568x1056（3:2，Z.AI）",
    "sd_res_1056x1568" to "1056x1568（2:3，Z.AI）",
    "sd_res_1472x1088" to "1472x1088（4:3，Z.AI）",
    "sd_res_1088x1472" to "1088x1472（3:4，Z.AI）",
    "sd_res_1728x960" to "1728x960（16:9，Z.AI）",
    "sd_res_960x1728" to "960x1728（9:16，Z.AI）",
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
    var deeplEndpoint by rememberSaveable { mutableStateOf(ServicesPrefs.translateDeeplEndpoint(context)) }
    var apiKey by rememberSaveable { mutableStateOf(ServicesPrefs.translateApiKey(context)) }
    var url by rememberSaveable { mutableStateOf(ServicesPrefs.translateUrl(context)) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    fun save() = ServicesPrefs.saveTranslate(context, provider, autoMode, target, apiKey)
    fun saveDeeplEndpoint(v: String) { deeplEndpoint = v; ServicesPrefs.saveTranslate(context, provider, autoMode, target, apiKey, deeplEndpoint = v) }
    fun saveUrl(v: String) { url = v; ServicesPrefs.saveTranslateUrl(context, v) }

    ServiceCard(title = "翻译") {
        ServiceNote("官方 translate 扩展：自动模式 / 提供商 / 目标语言 / API Key。聊天长按消息可翻译。")
        MenuPicker("自动翻译", labelOf(TRANSLATE_MODES, autoMode), TRANSLATE_MODES) { autoMode = it; save() }
        MenuPicker("提供商", labelOf(TRANSLATE_PROVIDERS, provider), TRANSLATE_PROVIDERS) { provider = it; save() }
        // 官方 #deepl_api_endpoint：仅 DeepL 时显示（index.js:159/172）
        if (provider == "deepl") {
            MenuPicker("DeepL 端点", labelOf(DEEPL_ENDPOINTS, deeplEndpoint), DEEPL_ENDPOINTS) { saveDeeplEndpoint(it) }
        }
        MenuPicker("目标语言", labelOf(TARGET_LANGUAGES, target), TARGET_LANGUAGES) { target = it; save() }
        KeyRow(
            value = apiKey,
            visible = keyVisible,
            onVisibleChange = { keyVisible = it },
            onValueChange = { apiKey = it; save() },
            label = "API Key（按提供商需要）",
        )
        // 官方 URL 输入仅 LOCAL_URL 四家提供商可见（libre/onering/deeplx/lingva）
        if (provider in LOCAL_URL_PROVIDERS) {
            TextFieldRow("接口地址（可空=官方默认）", url) { saveUrl(it) }
        }
    }
}

@Composable
private fun ImageCard() {
    val context = LocalContext.current
    var source by rememberSaveable { mutableStateOf(ServicesPrefs.imageSource(context)) }
    // 官方逐源 URL/auth（sd_auto_url+auth / sd_sdcpp_url / sd_vlad_url+auth / sd_drawthings_url+auth / comfy 两键）
    var autoUrl by rememberSaveable { mutableStateOf(ServicesPrefs.autoUrl(context)) }
    var autoAuth by rememberSaveable { mutableStateOf(ServicesPrefs.autoAuth(context)) }
    var sdcppUrl by rememberSaveable { mutableStateOf(ServicesPrefs.sdcppUrl(context)) }
    var vladUrl by rememberSaveable { mutableStateOf(ServicesPrefs.vladUrl(context)) }
    var vladAuth by rememberSaveable { mutableStateOf(ServicesPrefs.vladAuth(context)) }
    var drawthingsUrl by rememberSaveable { mutableStateOf(ServicesPrefs.drawthingsUrl(context)) }
    var drawthingsAuth by rememberSaveable { mutableStateOf(ServicesPrefs.drawthingsAuth(context)) }
    var comfyUrl by rememberSaveable { mutableStateOf(ServicesPrefs.comfyUrl(context)) }
    var comfyRunpodUrl by rememberSaveable { mutableStateOf(ServicesPrefs.comfyRunpodUrl(context)) }
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
    var snap by rememberSaveable { mutableStateOf(ServicesPrefs.imageSnap(context)) }
    var minimalPromptProcessing by rememberSaveable { mutableStateOf(ServicesPrefs.imageMinimalPromptProcessing(context)) }
    var novelAnlasGuard by rememberSaveable { mutableStateOf(ServicesPrefs.novelAnlasGuard(context)) }
    var novelSm by rememberSaveable { mutableStateOf(ServicesPrefs.novelSm(context)) }
    var novelSmDyn by rememberSaveable { mutableStateOf(ServicesPrefs.novelSmDyn(context)) }
    var novelDecrisper by rememberSaveable { mutableStateOf(ServicesPrefs.novelDecrisper(context)) }
    var novelVarietyBoost by rememberSaveable { mutableStateOf(ServicesPrefs.novelVarietyBoost(context)) }
    var hordeKarras by rememberSaveable { mutableStateOf(ServicesPrefs.imageHordeKarras(context)) }
    var hordeSanitize by rememberSaveable { mutableStateOf(ServicesPrefs.imageHordeSanitize(context)) }
    var hordeNsfw by rememberSaveable { mutableStateOf(ServicesPrefs.imageHordeNsfw(context)) }
    var openaiStyle by rememberSaveable { mutableStateOf(ServicesPrefs.openaiStyle(context)) }
    var openaiQuality by rememberSaveable { mutableStateOf(ServicesPrefs.openaiQuality(context)) }
    var openaiQualityGpt by rememberSaveable { mutableStateOf(ServicesPrefs.openaiQualityGpt(context)) }
    var stabilityStylePreset by rememberSaveable { mutableStateOf(ServicesPrefs.stabilityStylePreset(context)) }
    var pollinationsEnhance by rememberSaveable { mutableStateOf(ServicesPrefs.pollinationsEnhance(context)) }
    var googleEnhance by rememberSaveable { mutableStateOf(ServicesPrefs.googleEnhance(context)) }
    var ehQuality by rememberSaveable { mutableStateOf(ServicesPrefs.electronhubQuality(context)) }
    var bflUpsampling by rememberSaveable { mutableStateOf(ServicesPrefs.bflUpsampling(context)) }
    var hfModelId by rememberSaveable { mutableStateOf(ServicesPrefs.huggingfaceModelId(context)) }
    fun save() = ServicesPrefs.saveImage(context, source, model, steps)
    /** 逐源 URL/auth 落盘（官方 sd_<source>_url / sd_<source>_auth 键名）。 */
    fun saveSd(key: String, v: String) = ServicesPrefs.saveImageString(context, key, v)
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
        // 官方逐源 URL/auth 字段 + 验证按钮（settings.html sd_auto/sdcpp/drawthings/vlad/comfy/
        // comfy_runpod_validate；服务端各 ping 路由语义见 ImageGenClient.pingSource）
        val scope = rememberCoroutineScope()
        fun validateButton() = TextButton(onClick = {
            scope.launch {
                val err = ImageGenClient().pingSource(context, source)
                Toast.makeText(
                    context,
                    if (err == null) "已连接" else "无法验证：$err",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }) {
            Text(
                when {
                    source == "comfy" && comfyType == "runpod_serverless" -> "验证 ComfyUI RunPod"
                    source == "comfy" -> "验证 ComfyUI"
                    else -> "测试连接"
                },
            )
        }
        when (source) {
            "auto" -> {
                TextFieldRow("接口地址（sd_auto_url）", autoUrl) { autoUrl = it; saveSd("sd_auto_url", it) }
                TextFieldRow("认证（sd_auto_auth，user:pass）", autoAuth) { autoAuth = it; saveSd("sd_auto_auth", it) }
                validateButton()
            }
            "sdcpp" -> {
                TextFieldRow("接口地址（sd_sdcpp_url）", sdcppUrl) { sdcppUrl = it; saveSd("sd_sdcpp_url", it) }
                validateButton()
            }
            "vlad" -> {
                TextFieldRow("接口地址（sd_vlad_url）", vladUrl) { vladUrl = it; saveSd("sd_vlad_url", it) }
                TextFieldRow("认证（sd_vlad_auth，user:pass）", vladAuth) { vladAuth = it; saveSd("sd_vlad_auth", it) }
                validateButton()
            }
            "drawthings" -> {
                TextFieldRow("接口地址（sd_drawthings_url）", drawthingsUrl) { drawthingsUrl = it; saveSd("sd_drawthings_url", it) }
                TextFieldRow("认证（sd_drawthings_auth，user:pass）", drawthingsAuth) { drawthingsAuth = it; saveSd("sd_drawthings_auth", it) }
                validateButton()
            }
            "comfy" -> {
                // 官方 data-sd-comfy-type 分区：standard 显示 comfy_url，runpod 显示 comfy_runpod_url
                if (comfyType == "runpod_serverless") {
                    TextFieldRow("RunPod 地址（sd_comfy_runpod_url）", comfyRunpodUrl) { comfyRunpodUrl = it; saveSd("sd_comfy_runpod_url", it) }
                } else {
                    TextFieldRow("接口地址（sd_comfy_url）", comfyUrl) { comfyUrl = it; saveSd("sd_comfy_url", it) }
                }
                validateButton()
            }
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
            if (comfyType == "runpod_serverless") {
                // 官方 #sd_runpod_key（manage api_key_comfy_runpod）：RunPod 密钥，Bearer 调 /run /health
                KeyRow(
                    value = apiKey,
                    visible = keyVisible,
                    onVisibleChange = { keyVisible = it },
                    onValueChange = { apiKey = it; ServicesPrefs.saveImageApiKey(context, it) },
                    label = "RunPod API Key",
                )
            }
            if (comfyType == "standard") {
                ComfyWorkflowSection()
            }
        }
        ShellInput(value = promptPrefix, onValueChange = { promptPrefix = it; saveAdvanced() }, label = "提示词前缀（sd_prompt_prefix）", minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
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
        // 官方 #sd_resolution 预设 + onResolutionChange（index.js L1095-L1107）：选中即写入宽高
        val presetId = ImageGenRequestEngine.RESOLUTION_OPTIONS
            .firstOrNull {
                it.second.first == width.toIntOrNull() && it.second.second == height.toIntOrNull()
            }?.first ?: ""
        MenuPicker(
            "分辨率预设",
            RESOLUTION_LABELS[presetId] ?: "自定义",
            ImageGenRequestEngine.RESOLUTION_OPTIONS.map { DropdownOption(it.first, RESOLUTION_LABELS[it.first] ?: it.first) },
        ) { id ->
            val size = ImageGenRequestEngine.RESOLUTION_OPTIONS.firstOrNull { it.first == id }?.second ?: return@MenuPicker
            // 官方先写 height 再写 width（onResolutionChange L1104-L1105）
            height = size.second.toString()
            width = size.first.toString()
            saveAdvanced()
        }
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
        // 官方 settings.html 通用区：snap / minimal_prompt_processing（L34-L40）
        ToggleRow("吸附已知分辨率（snap，SDXL 推荐）", snap) {
            snap = it; ServicesPrefs.saveImageModeToggle(context, "sd_snap", it)
        }
        ToggleRow("最小 prompt 后处理（保留 JSON 结构输出）", minimalPromptProcessing) {
            minimalPromptProcessing = it; ServicesPrefs.saveImageModeToggle(context, "sd_minimal_prompt_processing", it)
        }
        // ---- per-source 选项（官方 settings.html data-sd-source 区段 1:1）----
        if (source == "novel") {
            // 官方 novel 区（L164-L178 + L562-L579）
            ToggleRow("避免消耗 Anlas（免费档自动调参）", novelAnlasGuard) {
                novelAnlasGuard = it; ServicesPrefs.saveImageModeToggle(context, "sd_novel_anlas_guard", it)
            }
            ToggleRow("SMEA（高分辨率采样优化）", novelSm) {
                novelSm = it; ServicesPrefs.saveImageModeToggle(context, "sd_novel_sm", it)
            }
            if (novelSm) {
                // 官方 SMEA 关闭时禁用 DYN 输入（disabled），此处以隐藏等价呈现
                ToggleRow("DYN（变化更大，极高分辨率可能失败）", novelSmDyn) {
                    novelSmDyn = it; ServicesPrefs.saveImageModeToggle(context, "sd_novel_sm_dyn", it)
                }
            }
            ToggleRow("Decrisper（降低高 CFG 伪影）", novelDecrisper) {
                novelDecrisper = it; ServicesPrefs.saveImageModeToggle(context, "sd_novel_decrisper", it)
            }
            ToggleRow("Variety+（提升多样性饱和度）", novelVarietyBoost) {
                novelVarietyBoost = it; ServicesPrefs.saveImageModeToggle(context, "sd_novel_variety_boost", it)
            }
        }
        if (source == "horde") {
            // 官方 horde 区（L149-L162）：nsfw 直连因官方服务端笔误从不生效；sanitize 默认开
            ToggleRow("允许 NSFW 图像（直连路径官方不生效）", hordeNsfw) {
                hordeNsfw = it; ServicesPrefs.saveImageModeToggle(context, "sd_horde_nsfw", it)
            }
            ToggleRow("清洗提示词（推荐）", hordeSanitize) {
                hordeSanitize = it; ServicesPrefs.saveImageModeToggle(context, "sd_horde_sanitize", it)
            }
            ToggleRow("Karras（部分采样器不支持；extras 路径生效）", hordeKarras) {
                hordeKarras = it; ServicesPrefs.saveImageModeToggle(context, "sd_horde_karras", it)
            }
        }
        if (source == "openai") {
            // 官方按模型族显示（data-sd-model，settings.html L192-L226）
            val m = model.lowercase()
            if (m.contains("dall-e-3")) {
                MenuPicker(
                    "图像风格（dall-e-3）",
                    if (openaiStyle == "natural") "Natural" else "Vivid",
                    listOf(DropdownOption("vivid", "Vivid"), DropdownOption("natural", "Natural")),
                ) {
                    openaiStyle = it; ServicesPrefs.saveImageString(context, "sd_openai_style", it)
                }
                MenuPicker(
                    "图像质量（dall-e-3）",
                    if (openaiQuality == "hd") "HD" else "Standard",
                    listOf(DropdownOption("standard", "Standard"), DropdownOption("hd", "HD")),
                ) {
                    openaiQuality = it; ServicesPrefs.saveImageString(context, "sd_openai_quality", it)
                }
            }
            if (m.contains("gpt-image")) {
                MenuPicker(
                    "图像质量（gpt-image）",
                    when (openaiQualityGpt) {
                        "low" -> "Low"; "medium" -> "Medium"; "high" -> "High"; else -> "Auto"
                    },
                    listOf(
                        DropdownOption("auto", "Auto"), DropdownOption("low", "Low"),
                        DropdownOption("medium", "Medium"), DropdownOption("high", "High"),
                    ),
                ) {
                    openaiQualityGpt = it; ServicesPrefs.saveImageString(context, "sd_openai_quality_gpt", it)
                }
            }
        }
        if (source == "stability") {
            // 官方 stability 区 Style Preset（settings.html L320-L342，17 项，默认 anime）
            val presets = listOf(
                "anime" to "Anime", "3d-model" to "3D Model", "analog-film" to "Analog Film",
                "cinematic" to "Cinematic", "comic-book" to "Comic Book", "digital-art" to "Digital Art",
                "enhance" to "Enhance", "fantasy-art" to "Fantasy Art", "isometric" to "Isometric",
                "line-art" to "Line Art", "low-poly" to "Low Poly", "modeling-compound" to "Modeling Compound",
                "neon-punk" to "Neon Punk", "origami" to "Origami", "photographic" to "Photographic",
                "pixel-art" to "Pixel Art", "tile-texture" to "Tile Texture",
            )
            MenuPicker(
                "风格预设（style_preset）",
                presets.firstOrNull { it.first == stabilityStylePreset }?.second ?: "Anime",
                presets.map { DropdownOption(it.first, it.second) },
            ) {
                stabilityStylePreset = it; ServicesPrefs.saveImageString(context, "sd_stability_style_preset", it)
            }
        }
        if (source == "google") {
            // 官方 google 区（settings.html L393-L417）：API Type 下拉 + enhance 默认开；
            // duration 属 Veo 视频分支（未接，登记偏差）
            var googleApi by rememberSaveable { mutableStateOf(ServicesPrefs.googleApi(context)) }
            MenuPicker(
                "API 类型（sd_google_api）",
                if (googleApi == "vertexai") "Google Vertex AI" else "Google AI Studio",
                listOf(
                    DropdownOption("makersuite", "Google AI Studio"),
                    DropdownOption("vertexai", "Google Vertex AI"),
                ),
            ) {
                googleApi = it; ServicesPrefs.saveImageString(context, "sd_google_api", it)
            }
            ToggleRow("Enhance（LLM 提示词增强）", googleEnhance) {
                googleEnhance = it; ServicesPrefs.saveImageModeToggle(context, "sd_google_enhance", it)
            }
        }
        if (source == "electronhub") {
            // 官方 ensureElectronHubQualitySelect（index.js L2047-L2084）：拉 /v1/models 取当前模型
            // qualities；无 qualities 隐藏行并置 undefined；有则未选时默认第一项并落盘
            var ehQualities by remember { mutableStateOf<List<String>>(emptyList()) }
            LaunchedEffect(source, model, apiKey) {
                ehQualities = ImageGenBackendsCloud.electronhubFetchQualities(model, apiKey)
                if (ehQualities.isNotEmpty() && !ehQualities.contains(ehQuality)) {
                    ehQuality = ehQualities.first()
                    ServicesPrefs.saveImageString(context, "sd_electronhub_quality", ehQuality)
                }
            }
            if (ehQualities.isNotEmpty()) {
                val effective = if (ehQualities.contains(ehQuality)) ehQuality else ehQualities.first()
                MenuPicker(
                    "图像质量（electronhub）",
                    effective,
                    ehQualities.map { DropdownOption(it, it) },
                ) {
                    ehQuality = it; ServicesPrefs.saveImageString(context, "sd_electronhub_quality", it)
                }
            }
        }
        if (source == "pollinations") {
            // 官方 pollinations 区：enhance 开关（settings.html #sd_pollinations_enhance）
            ToggleRow("Enhance（LLM 提示词增强）", pollinationsEnhance) {
                pollinationsEnhance = it; ServicesPrefs.saveImageModeToggle(context, "sd_pollinations_enhance", it)
            }
        }
        if (source == "bfl") {
            ToggleRow("Prompt Upsampling（自动润色提示词）", bflUpsampling) {
                bflUpsampling = it; ServicesPrefs.saveImageModeToggle(context, "sd_bfl_upsampling", it)
            }
        }
        if (source == "huggingface") {
            TextFieldRow("Model ID（如 black-forest-labs/FLUX.1-dev）", hfModelId) {
                hfModelId = it; ServicesPrefs.saveImageString(context, "sd_huggingface_model_id", it)
            }
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
                ShellChip("Workflow：$active", selected = true) { selectorOpen = true }
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
        // 官方 comfyWorkflowEditor：自定义占位符随弹窗打开载入、保存时落盘
        var customPhs by remember { mutableStateOf(ServicesPrefs.comfyPlaceholders(context)) }
        // 官方 comfyWorkflowEditor.html 标准清单（含 seed 特殊项；user_avatar/char_avatar 头像注入未接，登记偏差）
        val standardKeys = listOf(
            "prompt", "negative_prompt", "model", "vae", "sampler", "scheduler",
            "steps", "scale", "denoise", "clip_skip", "width", "height", "user_avatar", "char_avatar",
        )
        fun mark(key: String) = if (draftJson.contains("\"%$key%\"")) "✅" else "❌"
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
                    // 官方 checkPlaceholders：✅=workflow 里存在 "%key%"，❌=缺失（随输入实时刷新）
                    Text(
                        standardKeys.joinToString(" ") { "${mark(it)} %$it%" } +
                            "  ${mark("seed")} %seed%（每次生成随机）",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmberTheme.colors.inkMute,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "自定义占位符",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { customPhs = customPhs + Pair("", "") }) { Text("＋ 添加") }
                    }
                    customPhs.forEachIndexed { i, ph ->
                        val key = ph.first
                        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${mark(key)} \"%$key%\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmberTheme.colors.inkMute,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { customPhs = customPhs.filterIndexed { j, _ -> j != i } }) {
                                    Text("⊘")
                                }
                            }
                            ShellInput(
                                value = ph.first,
                                onValueChange = { v -> customPhs = customPhs.mapIndexed { j, p -> if (j == i) Pair(v, p.second) else p } },
                                label = "find（workflow 里的 %…%）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ShellInput(
                                value = ph.second,
                                onValueChange = { v -> customPhs = customPhs.mapIndexed { j, p -> if (j == i) Pair(p.first, v) else p } },
                                label = "replace（支持 {{user}} 等宏）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draftJson.trim().isNotEmpty()) {
                        store.write(active, draftJson)
                        ServicesPrefs.saveComfyPlaceholders(context, customPhs)
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
                ShellChip(
                    if (active.isBlank()) "样式库（未选择）" else "样式：$active",
                    selected = active.isNotBlank(),
                ) { selectorOpen = true }
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
                ShellChip(pv.label, selected = provider == pv.value) {
                    provider = pv.value; save()
                }
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
    // E0 平面分组（DESIGN_SYSTEM §一-1）：组题 + 留白即分隔，无卡片框
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            color = EmberTheme.colors.inkMute,
            fontSize = EmberTheme.typo.meta.fontSize,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
        )
        content()
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
    ShellInput(value = value, onValueChange = onValueChange, label = label, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
private fun DecimalRow(label: String, value: String, onValueChange: (String) -> Unit) {
    ShellInput(value = value, onValueChange = onValueChange, label = label, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
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
    ShellInput(value = value, onValueChange = onValueChange, label = label, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun KeyRow(
    value: String,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    label: String,
) {
    ShellInput(value = value, onValueChange = onValueChange, label = label, singleLine = true, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailing = {

            TextButton(onClick = { onVisibleChange(!visible) }) {
                Text(if (visible) "隐藏" else "显示")
            }
        
}, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
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


