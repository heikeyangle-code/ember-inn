package com.emberinn.engine.prompt

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 官方 TTS 扩展各后端 fetchTtsGeneration / fetchNativeTtsGeneration 构造的 request body（1:1）。
 *
 * 差分：
 * - scripts/diff/tts-services-official.mjs（35 例：11 云端后端全覆盖已接云端）。
 * - scripts/diff/tts-local-official.mjs（38 例：13 本地后端——alltalk/chatterbox/coqui/cosyvoice/
 *   gpt-sovits-adapter/gpt-sovits-v2/gsvi/sbvits2/silerotts/speecht5/tts-webui/vits/xtts）。
 *
 * 官方函数逐字摘自 SillyTavern 1.18.0 release 8172dcd：
 *   public/scripts/extensions/tts/{backend}.js 的 fetchTtsGeneration 函数体内 body/URL 字面量构造段
 *   （无 fetchTtsGeneration 者对照 generateTts 内联构造段）。
 *
 * 重要语义：
 * - JS `Number(x) || default`：x 为空字符串/0/未定义 → 用 default；x 为非零数字 → 用 x。
 *   Kotlin 用 `numOrDefault` 等价实现（解析失败/0 → default）。
 * - JS `Math.round`：四舍五入到整数（half up，与 Kotlin Math.round 一致，但 Kotlin 用 RoundingMode.HALF_UP 通过 toDouble().roundToInt）。
 * - JS `splitRecursive`：分块函数，已移植（见 splitRecursive），保证 novel/pollinations/google-translate 输出与官方一致。
 * - JS `URLSearchParams.toString()` → application/x-www-form-urlencoded（space=+，与 Java URLEncoder.encode 一致）。
 * - JS `parseInt('none')` = NaN → JSON.stringify(NaN) = null；Kotlin 用 toIntOrNull() ?: JsonNull。
 * - JS `String(undefined)` = "undefined"（URLSearchParams.append 对 undefined 取 string）。
 * - JS `Number.toString()`：整数值不带小数点（1.0 → "1"）；Kotlin 用 `numStr` 等价。
 * - JS `Boolean.toString()`：true/false；Kotlin 直接用 "true"/"false"。
 *
 * 边界（不差分，登记）：
 * - 服务端 src/endpoints/{speech,openai,google,azure,volcengine,minimax,novelai}.js 把 .js body 转发到厂商
 *   真实端点时可能做字段映射（如 OpenAI text→input）；该映射属服务端实现，App 直连时按厂商 API
 *   字段名另做适配（见 TtsBackendsCloud），不在本差分覆盖。
 * - history 复用、voice cloning、recognize、preview 等非生成行为不差分。
 * - kokoro.js / kokoro-worker.js：官方为浏览器 WebWorker（postMessage，无 HTTP 请求体），与 App
 *   POST {endpoint}/tts {text,voice} 不同源——登记不差分（见 HANDOFF 4.4）。
 * - openai-compatible.js：官方走 ST 代理 /api/openai/custom/generate-voice（body 含 provider_endpoint
 *   字段），App 直连厂商 /v1/audio/speech（body 不含）——不同源，登记不差分。
 * - speecht5.js：speaker 字段取自 getVoice(voiceId).data（运行时拉取的说话人嵌入），App 用 voiceId
 *   直接传入——值源不同但 body 字段集合一致；此处差分 body 构造（speaker 作不透明入参）。
 * - xtts.js processText 在 fetchTtsGeneration 不调用（死代码），不差分；App 也不调用。
 * - 各后端 getVoices / fetchTtsVoiceObjects / previewTtsVoice / changeTTSSettings / fetchTtsFromHistory
 *   等非生成行为不差分。
 */
object TtsRequestEngine {

    // ============ ElevenLabs（elevenlabs.js fetchTtsGeneration L332-L361 逐字摘） ============
    // defaultSettings: stability=0.75, similarity_boost=0.75, style_exaggeration=0.0, speaker_boost=true, speed=1.0, model=eleven_turbo_v2_5
    // request = { model_id, text, voice_settings:{ stability, similarity_boost, speed, [style, use_speaker_boost] } }
    // 分支：shouldInvolveExtendedSettings = model ∈ [eleven_v3,eleven_ttv_v3,eleven_multilingual_v2,eleven_multilingual_ttv_v2]
    private val ELEVENLABS_EXTENDED_MODELS = setOf("eleven_v3", "eleven_ttv_v3", "eleven_multilingual_v2", "eleven_multilingual_ttv_v2")

    private fun elevenlabsShouldInvolveExtendedSettings(model: String): Boolean = model in ELEVENLABS_EXTENDED_MODELS

    /**
     * ElevenLabs 客户端→服务端 body = { voiceId, request:{ model_id, text, voice_settings:{...} } }
     * 调用方需传完整 settings（含 stability/similarity_boost/speed/style_exaggeration/speaker_boost/model）。
     */
    fun elevenLabsRequestBody(
        settings: JsonObject,
        text: String,
        voiceId: String,
    ): JsonObject {
        val model = settings.strOr("model", "eleven_monolingual_v1")
        val voiceSettings = buildJsonObject {
            put("stability", num(settings.numOrDefault("stability", 0.75)))
            put("similarity_boost", num(settings.numOrDefault("similarity_boost", 0.75)))
            put("speed", num(settings.numOrDefault("speed", 1.0)))
            // 分支：shouldInvolveExtendedSettings → 加 style + use_speaker_boost
            if (elevenlabsShouldInvolveExtendedSettings(model)) {
                put("style", num(settings.numOrDefault("style_exaggeration", 0.0)))
                put("use_speaker_boost", JsonPrimitive(settings.boolOrDefault("speaker_boost", true)))
            }
        }
        return buildJsonObject {
            put("voiceId", JsonPrimitive(voiceId))
            put("request", buildJsonObject {
                put("model_id", JsonPrimitive(model))
                put("text", JsonPrimitive(text))
                put("voice_settings", voiceSettings)
            })
        }
    }

    // ============ OpenAI（openai.js fetchTtsGeneration L222-L251 逐字摘） ============
    // requestBody = { text, voice, model, speed, [instructions] }
    // 分支：model='gpt-4o-mini-tts' && characterName && instructions.trim() → 加 instructions=substituteParams(instructions)
    fun openAiRequestBody(
        settings: JsonObject,
        inputText: String,
        voiceId: String,
        characterName: String?,
        characterInstructions: String?,
    ): JsonObject {
        val model = settings.strOr("model", "tts-1")
        val speed = settings.numOrDefault("speed", 1.0)
        // 分支：model='gpt-4o-mini-tts' && characterName && instructions.trim() → 加 instructions
        val addInstructions = model == "gpt-4o-mini-tts" && characterName != null &&
            !characterInstructions.orEmpty().trim().isEmpty()
        return buildJsonObject {
            put("text", JsonPrimitive(inputText))
            put("voice", JsonPrimitive(voiceId))
            put("model", JsonPrimitive(model))
            put("speed", num(speed))
            if (addInstructions) {
                // substituteParams 打桩为恒等（macro 替换由 macros-official.mjs 覆盖）
                put("instructions", JsonPrimitive(characterInstructions))
            }
        }
    }

    // ============ Edge（edge.js fetchTtsGeneration L167-L188 逐字摘） ============
    // body = { text, voice, rate }
    fun edgeRequestBody(settings: JsonObject, inputText: String, voiceId: String): JsonObject = buildJsonObject {
        put("text", JsonPrimitive(inputText))
        put("voice", JsonPrimitive(voiceId))
        put("rate", num(settings.numOrDefault("rate", 0.0)))
    }

    // ============ Azure（azure.js fetchTtsGeneration L182-L207 逐字摘） ============
    // body = { text, voice, region }
    fun azureRequestBody(settings: JsonObject, text: String, voiceId: String): JsonObject = buildJsonObject {
        put("text", JsonPrimitive(text))
        put("voice", JsonPrimitive(voiceId))
        put("region", JsonPrimitive(settings.strOr("region", "")))
    }

    // ============ NovelAI（novel.js fetchTtsGeneration L193-L214 逐字摘） ============
    // generator：splitRecursive(inputText,1000) 分块，每块 body = { text: chunk, voice }
    // fixture 只输出第一块 body（chunking 是分块调度，body 字段一致）
    fun novelRequestBody(settings: JsonObject, inputText: String, voiceId: String): JsonObject {
        val chunks = splitRecursive(inputText, 1000)
        val chunk = chunks.firstOrNull().orEmpty()
        return buildJsonObject {
            put("text", JsonPrimitive(chunk))
            put("voice", JsonPrimitive(voiceId))
        }
    }

    // ============ MiniMax（minimax.js fetchTtsGeneration L775-L799 逐字摘） ============
    // 含 clamp 与 defaultSettings 兜底
    private val MINIMAX_DEFAULTS = MinimaxDefaults(
        model = "speech-02-hd",
        speedDefault = 1.0, speedMin = 0.5, speedMax = 2.0,
        volumeDefault = 50.0, volumeMin = 1.0, volumeMax = 100.0,
        pitchDefault = 0.0, pitchMin = -100.0, pitchMax = 100.0,
        audioSampleRate = 32000.0,
        bitrate = 128000.0,
        format = "mp3",
    )

    private data class MinimaxDefaults(
        val model: String,
        val speedDefault: Double, val speedMin: Double, val speedMax: Double,
        val volumeDefault: Double, val volumeMin: Double, val volumeMax: Double,
        val pitchDefault: Double, val pitchMin: Double, val pitchMax: Double,
        val audioSampleRate: Double,
        val bitrate: Double,
        val format: String,
    )

    fun minimaxRequestBody(
        settings: JsonObject,
        inputText: String,
        voiceId: String,
        language: String?,
    ): JsonObject = buildJsonObject {
        val clamp: (Double, Double, Double) -> Double = { n, lower, upper -> n.coerceIn(lower, upper) }
        put("text", JsonPrimitive(inputText))
        put("voiceId", JsonPrimitive(voiceId))
        put("apiHost", JsonPrimitive(settings.strOr("apiHost", "")))
        put("model", JsonPrimitive(settings.strOr("model", "").ifBlank { MINIMAX_DEFAULTS.model }))
        // speed: clamp(Number(settings.speed) || default, min, max)
        val speed = clamp(settings.numOrDefault("speed", MINIMAX_DEFAULTS.speedDefault), MINIMAX_DEFAULTS.speedMin, MINIMAX_DEFAULTS.speedMax)
        put("speed", num(speed))
        // volume
        val volume = clamp(settings.numOrDefault("volume", MINIMAX_DEFAULTS.volumeDefault), MINIMAX_DEFAULTS.volumeMin, MINIMAX_DEFAULTS.volumeMax)
        put("volume", num(volume))
        // pitch: clamp(Math.round(Number(settings.pitch)) || default, min, max)
        val pitchRaw = settings.numOrDefault("pitch", MINIMAX_DEFAULTS.pitchDefault)
        val pitchRounded = kotlin.math.round(pitchRaw)
        val pitch = if (pitchRounded == 0.0) MINIMAX_DEFAULTS.pitchDefault else pitchRounded
        put("pitch", num(clamp(pitch, MINIMAX_DEFAULTS.pitchMin, MINIMAX_DEFAULTS.pitchMax)))
        // audioSampleRate
        put("audioSampleRate", num(settings.numOrDefault("audioSampleRate", MINIMAX_DEFAULTS.audioSampleRate)))
        // bitrate
        put("bitrate", num(settings.numOrDefault("bitrate", MINIMAX_DEFAULTS.bitrate)))
        // format
        put("format", JsonPrimitive(settings.strOr("format", "").ifBlank { MINIMAX_DEFAULTS.format }))
        // language（null 透传，对齐官方 body 字段）
        if (language != null) put("language", JsonPrimitive(language)) else put("language", JsonNull)
    }

    // ============ Volcengine（volcengine.js fetchTtsGeneration L295-L315 逐字摘） ============
    // body = { provider_endpoint, resource_id, text, voice_speaker, speed }
    // speed 原样透传无兜底（官方 settings.speed 默认 0；键缺失时 JSON.stringify 丢弃 undefined）
    fun volcengineRequestBody(settings: JsonObject, text: String, voiceSpeaker: String): JsonObject = buildJsonObject {
        put("provider_endpoint", JsonPrimitive(settings.strOr("provider_endpoint", "")))
        put("resource_id", JsonPrimitive(settings.strOr("resource_id", "")))
        put("text", JsonPrimitive(text))
        put("voice_speaker", JsonPrimitive(voiceSpeaker))
        if (settings.containsKey("speed")) put("speed", num(settings.numOrDefault("speed", 0.0)))
    }

    // ============ Chutes（chutes.js fetchTtsGeneration L194-L217 逐字摘） ============
    // body = { input, voice: voiceId || 'af_heart', speed: settings.speed || 1 }
    fun chutesRequestBody(settings: JsonObject, text: String, voiceId: String?): JsonObject = buildJsonObject {
        put("input", JsonPrimitive(text))
        // JS || 短路：空串/null 视同 falsy → 'af_heart'
        put("voice", JsonPrimitive(voiceId?.takeIf { it.isNotBlank() } ?: "af_heart"))
        // speed || 1：0/未定义 → 1
        val sp = settings.numOrDefault("speed", 1.0)
        put("speed", num(if (sp == 0.0) 1.0 else sp))
    }

    // ============ Pollinations（pollinations.js fetchTtsGeneration L128-L149 逐字摘） ============
    // generator：splitRecursive(text,1000) 分块，每块 body = { model, text:'Say exactly this and nothing else:'+'\n'+chunk, voice }
    fun pollinationsRequestBody(settings: JsonObject, text: String, voiceId: String): JsonObject {
        val chunks = splitRecursive(text, 1000)
        val chunk = chunks.firstOrNull().orEmpty()
        return buildJsonObject {
            put("model", JsonPrimitive(settings.strOr("model", "")))
            put("text", JsonPrimitive("Say exactly this and nothing else:\n$chunk"))
            put("voice", JsonPrimitive(voiceId))
        }
    }

    // ============ Google Native（google-native.js fetchNativeTtsGeneration L160-L178 逐字摘） ============
    // body = { text, voice, model, api, reverse_proxy, proxy_password, vertexai_auth_mode, vertexai_region, vertexai_express_project_id }
    // useReverseProxy = oaiSettings.reverse_proxy && isValidUrl(oaiSettings.reverse_proxy)
    fun googleNativeRequestBody(
        settings: JsonObject,
        text: String,
        voiceId: String,
        oaiSettings: JsonObject,
    ): JsonObject {
        val reverseProxy = oaiSettings.strOr("reverse_proxy", "")
        val useReverseProxy = reverseProxy.isNotEmpty() && isValidUrl(reverseProxy)
        return buildJsonObject {
            put("text", JsonPrimitive(text))
            put("voice", JsonPrimitive(voiceId))
            put("model", JsonPrimitive(settings.strOr("model", "")))
            put("api", JsonPrimitive(settings.strOr("apiType", "generate")))
            put("reverse_proxy", JsonPrimitive(if (useReverseProxy) reverseProxy else ""))
            put("proxy_password", JsonPrimitive(if (useReverseProxy) oaiSettings.strOr("proxy_password", "") else ""))
            put("vertexai_auth_mode", JsonPrimitive(oaiSettings.strOr("vertexai_auth_mode", "")))
            put("vertexai_region", JsonPrimitive(oaiSettings.strOr("vertexai_region", "")))
            put("vertexai_express_project_id", JsonPrimitive(oaiSettings.strOr("vertexai_express_project_id", "")))
        }
    }

    // ============ Google Translate（google-translate.js fetchTtsGeneration L123-L139 逐字摘） ============
    // body = { text: splitRecursive(text, 200), voice }
    fun googleTranslateRequestBody(settings: JsonObject, text: String, voiceId: String): JsonObject = buildJsonObject {
        val chunks = splitRecursive(text, 200)
        putJsonArray("text") {
            chunks.forEach { add(JsonPrimitive(it)) }
        }
        put("voice", JsonPrimitive(voiceId))
    }

    // ====================================================================
    // 本地后端（tts-local-official.mjs 38 例差分）
    // 官方函数逐字摘自 public/scripts/extensions/tts/{backend}.js 的 fetchTtsGeneration
    // （无 fetchTtsGeneration 者对照 generateTts 内联构造段）。
    // ====================================================================

    // ============ AllTalk（alltalk.js fetchTtsGeneration L990-L1015 逐字摘） ============
    // requestBody = new URLSearchParams({ 11 字段 }); V2 + RVC 条件 append 4 字段。
    // 返回 { form: "k1=v1&k2=v2..." }（form 用 URLSearchParams.toString 语义，space=+）。
    fun allTalkForm(settings: JsonObject, inputText: String, voiceId: String): JsonObject {
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += "text_input" to inputText
        pairs += "text_filtering" to "standard"
        pairs += "character_voice_gen" to voiceId
        pairs += "narrator_enabled" to settings.primStrOr("narrator_enabled", "")
        pairs += "narrator_voice_gen" to settings.primStrOr("narrator_voice_gen", "")
        pairs += "text_not_inside" to settings.primStrOr("at_narrator_text_not_inside", "")
        pairs += "language" to settings.primStrOr("language", "")
        pairs += "output_file_name" to "st_output"
        pairs += "output_file_timestamp" to "true"
        pairs += "autoplay" to "false"
        pairs += "autoplay_volume" to "0.8"
        // V2 分支：RVC 字段条件 append
        if (settings.primStrOr("server_version", "") == "v2") {
            val rvcCharVoice = settings.primStrOr("rvc_character_voice", "")
            if (rvcCharVoice != "Disabled") {
                pairs += "rvccharacter_voice_gen" to rvcCharVoice
                // JS: settings.rvc_character_pitch || '0'（空串/undefined → '0'）
                pairs += "rvccharacter_pitch" to (settings.primStrOr("rvc_character_pitch", "").ifBlank { "0" })
            }
            val rvcNarVoice = settings.primStrOr("rvc_narrator_voice", "")
            if (rvcNarVoice != "Disabled") {
                pairs += "rvcnarrator_voice_gen" to rvcNarVoice
                pairs += "rvcnarrator_pitch" to (settings.primStrOr("rvc_narrator_pitch", "").ifBlank { "0" })
            }
        }
        return buildJsonObject {
            put("form", JsonPrimitive(pairs.joinToString("&") { (k, v) -> "${formEncode(k)}=${formEncode(v)}" }))
        }
    }

    // ============ Chatterbox（chatterbox.js generateTts L572-L605 逐字摘） ============
    // body = { text, voice_mode, temperature, exaggeration, cfg_weight, seed, speed_factor, language,
    //   split_text, chunk_size, output_format, [reference_audio_filename | predefined_voice_id] }
    // seed: settings.seed >= 0 ? settings.seed : Math.floor(Math.random() * 2147483648)
    //   App 传 randomSeed（随机）；差分 fixture 打桩 Math.random=0 → 0。
    // voiceId 'ref_' 前缀 → clone 模式（reference_audio_filename）；否则 predefined（predefined_voice_id）。
    fun chatterboxBody(
        settings: JsonObject,
        inputText: String,
        voiceId: String,
        randomSeed: Long,
    ): JsonObject {
        var isReferenceVoice = false
        var actualVoiceId = voiceId
        if (voiceId.isNotEmpty() && voiceId.startsWith("ref_")) {
            isReferenceVoice = true
            actualVoiceId = voiceId.substring(4)
        }
        val seed = settings.numOrLong("seed").let { if (it >= 0) it else randomSeed }
        val predefined = actualVoiceId.ifBlank { settings.primStrOr("predefined_voice", "") }
        return buildJsonObject {
            put("text", JsonPrimitive(inputText))
            put("voice_mode", JsonPrimitive(if (isReferenceVoice) "clone" else "predefined"))
            put("temperature", num(settings.numOrDouble("temperature")))
            put("exaggeration", num(settings.numOrDouble("exaggeration")))
            put("cfg_weight", num(settings.numOrDouble("cfg_weight")))
            put("seed", JsonPrimitive(seed))
            put("speed_factor", num(settings.numOrDouble("speed_factor")))
            put("language", JsonPrimitive(settings.primStrOr("language", "")))
            put("split_text", JsonPrimitive(settings.boolOrFalse("split_text")))
            put("chunk_size", JsonPrimitive(settings.numOrLong("chunk_size")))
            put("output_format", JsonPrimitive(settings.primStrOr("output_format", "")))
            // 分支：clone → reference_audio_filename；predefined → predefined_voice_id = actualVoiceId || settings.predefined_voice
            if (isReferenceVoice) {
                put("reference_audio_filename", JsonPrimitive(actualVoiceId))
            } else {
                put("predefined_voice_id", JsonPrimitive(predefined))
            }
        }
    }

    // ============ Coqui（coqui.js generateTts L674-L714 逐字摘） ============
    // voiceId = settings.customVoices[voiceId]（先映射；App 无 customVoices → 恒等映射，打桩）
    // tokens = voiceId.replaceAll(']','').replaceAll('"','').split('[')
    // model_id = tokens[0]; language='none'; speaker='none'
    // tokens.length>1: model_id.includes('multilingual') ? language=tokens[1] : speaker=tokens[1]
    // tokens.length>2: speaker=tokens[2]
    // body = { text, model_id, language_id: parseInt(language), speaker_id: parseInt(speaker) }
    // parseInt('none')=NaN → JSON null
    fun coquiBody(settings: JsonObject, inputText: String, voiceId: String): JsonObject {
        // 打桩：customVoices 恒等映射（App 无此字段）
        val customVoices = settings["customVoices"] as? JsonObject
        val mapped = customVoices?.get(voiceId)?.jsonPrimitive?.contentOrNull ?: voiceId
        val cleaned = mapped.replace("]", "").replace("\"", "")
        val tokens = cleaned.split("[")
        val modelId = tokens.getOrElse(0) { "" }
        var language = "none"
        var speaker = "none"
        if (tokens.size > 1) {
            val option1 = tokens[1]
            if (modelId.contains("multilingual")) language = option1 else speaker = option1
        }
        if (tokens.size > 2) speaker = tokens[2]
        return buildJsonObject {
            put("text", JsonPrimitive(inputText))
            put("model_id", JsonPrimitive(modelId))
            // JS parseInt('none')=NaN → JSON.stringify(NaN)=null；Kotlin toIntOrNull() ?: JsonNull
            put("language_id", language.toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull)
            put("speaker_id", speaker.toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull)
        }
    }

    // ============ CosyVoice（cosyvoice.js fetchTtsGeneration L162-L187 逐字摘） ============
    // params = { text, speaker }; if (settings.streaming) params.streaming = 1; → body
    fun cosyVoiceBody(settings: JsonObject, inputText: String, voiceId: String): JsonObject = buildJsonObject {
        put("text", JsonPrimitive(inputText))
        put("speaker", JsonPrimitive(voiceId))
        // JS if (settings.streaming)：truthy 即加（true/"true"/1 等）；false/0/"" 不加
        if (settings.truthy("streaming")) {
            put("streaming", JsonPrimitive(1))
        }
    }

    // ============ GPT-SoVITS Adapter（gpt-sovits-adapter.js fetchTtsGeneration L195-L220 逐字摘） ============
    // params = { text, card_name: getCharacters(false), use_st_adapter: true, target_voice: voiceId,
    //   text_lang: settings.text_lang, text_split_method: 'cut5', batch_size: 1,
    //   media_type: settings.media_type, streaming_mode: 'true' }
    // 打桩：getCharacters(false) = ''（Android 无角色上下文）
    fun gptSoVitsAdapterBody(settings: JsonObject, inputText: String, voiceId: String): JsonObject = buildJsonObject {
        put("text", JsonPrimitive(inputText))
        put("card_name", JsonPrimitive(""))
        put("use_st_adapter", JsonPrimitive(true))
        put("target_voice", JsonPrimitive(voiceId))
        put("text_lang", JsonPrimitive(settings.primStrOr("text_lang", "")))
        put("text_split_method", JsonPrimitive("cut5"))
        put("batch_size", JsonPrimitive(1))
        put("media_type", JsonPrimitive(settings.primStrOr("media_type", "")))
        put("streaming_mode", JsonPrimitive("true"))
    }

    // ============ GPT-SoVITS V2（gpt-sovits-v2.js fetchTtsGeneration L169-L202 逐字摘） ============
    // replaceSpeaker(text) = text.replace(/\[.*?\]/gu, '')
    // params = { text, prompt_text: replaceSpeaker(voiceId), ref_audio_path: './参考音频/'+voiceId+'.wav',
    //   text_lang: settings.text_lang, prompt_lang: settings.prompt_lang, text_split_method: 'cut5',
    //   batch_size: 1, media_type: 'ogg', streaming_mode: 'true' }
    fun gptSoVitsV2Body(settings: JsonObject, inputText: String, voiceId: String): JsonObject {
        val promptText = voiceId.replace(Regex("""\[.*?\]"""), "")
        return buildJsonObject {
            put("text", JsonPrimitive(inputText))
            put("prompt_text", JsonPrimitive(promptText))
            put("ref_audio_path", JsonPrimitive("./参考音频/$voiceId.wav"))
            put("text_lang", JsonPrimitive(settings.primStrOr("text_lang", "")))
            put("prompt_lang", JsonPrimitive(settings.primStrOr("prompt_lang", "")))
            put("text_split_method", JsonPrimitive("cut5"))
            put("batch_size", JsonPrimitive(1))
            put("media_type", JsonPrimitive("ogg"))
            put("streaming_mode", JsonPrimitive("true"))
        }
    }

    // ============ GSVI（gsvi.js fetchTtsGeneration L235-L251 逐字摘） ============
    // params = new URLSearchParams(); append 9 字段；返回 url = endpoint + "/tts?" + params.toString()
    // 字段：text, cha_name, text_language, batch_size(toString), speed(toString), top_k(toString),
    //      top_p(toString), temperature(toString), stream(toString)
    // 返回 { query: "..." }（form 编码，space=+）
    fun gsviQuery(settings: JsonObject, inputText: String, voiceId: String): JsonObject {
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += "text" to inputText
        pairs += "cha_name" to voiceId
        pairs += "text_language" to settings.primStrOr("language", "")
        pairs += "batch_size" to settings.numStr("batch_size")
        pairs += "speed" to settings.numStr("speed")
        pairs += "top_k" to settings.numStr("top_k")
        pairs += "top_p" to settings.numStr("top_p")
        pairs += "temperature" to settings.numStr("temperature")
        pairs += "stream" to settings.boolStr("stream")
        return buildJsonObject {
            put("query", JsonPrimitive(pairs.joinToString("&") { (k, v) -> "${formEncode(k)}=${formEncode(v)}" }))
        }
    }

    // ============ Style-Bert-VITS2（sbvits2.js fetchTtsGeneration L276-L318 逐字摘） ============
    // [model_id, speaker_id, ...rest] = voiceId.split('-'); style = rest.join('-')
    // inputText = inputText.replaceAll('<br>', '\n')
    // params = new URLSearchParams(); append text, model_id, speaker_id, sdp_ratio, noise, noisew, length,
    //   language, auto_split, split_interval
    // if (settings.assist_text) append assist_text, assist_text_weight
    // append style, style_weight
    // if (settings.reference_audio_path) append reference_audio_path
    // 返回 { query: "..." }（form 编码，space=+）
    fun sbvits2Query(settings: JsonObject, inputText: String, voiceId: String): JsonObject {
        val parts = voiceId.split("-")
        // JS 解构：缺位为 undefined → URLSearchParams.append 取 "undefined"
        val modelId = parts.getOrNull(0) ?: "undefined"
        val speakerId = parts.getOrNull(1) ?: "undefined"
        val style = parts.drop(2).joinToString("-")
        val replacedText = inputText.replace("<br>", "\n")
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += "text" to replacedText
        pairs += "model_id" to modelId
        pairs += "speaker_id" to speakerId
        pairs += "sdp_ratio" to settings.numStr("sdp_ratio")
        pairs += "noise" to settings.numStr("noise")
        pairs += "noisew" to settings.numStr("noisew")
        pairs += "length" to settings.numStr("length")
        pairs += "language" to settings.primStrOr("language", "")
        pairs += "auto_split" to settings.boolStr("auto_split")
        pairs += "split_interval" to settings.numStr("split_interval")
        // JS if (settings.assist_text)：truthy 即加（非空串）
        if (settings.truthy("assist_text")) {
            pairs += "assist_text" to settings.primStrOr("assist_text", "")
            pairs += "assist_text_weight" to settings.numStr("assist_text_weight")
        }
        pairs += "style" to style
        pairs += "style_weight" to settings.numStr("style_weight")
        if (settings.truthy("reference_audio_path")) {
            pairs += "reference_audio_path" to settings.primStrOr("reference_audio_path", "")
        }
        return buildJsonObject {
            put("query", JsonPrimitive(pairs.joinToString("&") { (k, v) -> "${formEncode(k)}=${formEncode(v)}" }))
        }
    }

    // ============ Silero（silerotts.js fetchTtsGeneration L122-L144 逐字摘） ============
    // body = { text, speaker: voiceId, session: 'sillytavern' }
    fun sileroBody(inputText: String, voiceId: String): JsonObject = buildJsonObject {
        put("text", JsonPrimitive(inputText))
        put("speaker", JsonPrimitive(voiceId))
        put("session", JsonPrimitive("sillytavern"))
    }

    // ============ SpeechT5（speecht5.js fetchTtsGeneration L166-L186 逐字摘） ============
    // speaker = await this.getVoice(voiceId) → speaker.data（运行时拉取，App 传 voiceId 作 speaker）
    // body = { text, speaker: speakerData, model: 'Xenova/speecht5_tts' }
    // 差分：speaker 作不透明入参（App 用 voiceId 直接传入，值源不同但 body 字段集合一致）
    fun speechT5Body(inputText: String, speakerData: String): JsonObject = buildJsonObject {
        put("text", JsonPrimitive(inputText))
        put("speaker", JsonPrimitive(speakerData))
        put("model", JsonPrimitive("Xenova/speecht5_tts"))
    }

    // ============ TTS WebUI（tts-webui.js fetchTtsGeneration L489-L527 逐字摘） ============
    // chatterboxParams = [15 字段名]；getParams = Object.fromEntries(filter settings by chatterboxParams)
    // requestBody = { model: settings.model, voice: voiceId, input: inputText, response_format: 'wav',
    //   speed: settings.speed, stream: settings.streaming, params: getParams(settings) }
    fun ttsWebuiBody(settings: JsonObject, inputText: String, voiceId: String): JsonObject {
        val chatterboxParamKeys = listOf(
            "desired_length", "max_length", "halve_first_chunk", "exaggeration", "cfg_weight",
            "temperature", "device", "dtype", "cpu_offload", "chunked", "cache_voice",
            "tokens_per_slice", "remove_milliseconds", "remove_milliseconds_start",
            "chunk_overlap_method", "seed",
        )
        val params: JsonObject = buildJsonObject {
            for (key in chatterboxParamKeys) {
                val v = settings[key] ?: continue
                put(key, v)
            }
        }
        return buildJsonObject {
            put("model", JsonPrimitive(settings.primStrOr("model", "")))
            put("voice", JsonPrimitive(voiceId))
            put("input", JsonPrimitive(inputText))
            put("response_format", JsonPrimitive("wav"))
            put("speed", settings["speed"] ?: JsonNull)
            put("stream", settings["streaming"] ?: JsonNull)
            put("params", params)
        }
    }

    // ============ VITS（vits.js fetchTtsGeneration L317-L355 逐字摘） ============
    // streaming = !forceNoStreaming && settings.streaming
    // [model_type, speaker_id] = voiceId.split('&')
    // params = new URLSearchParams(); append text, id=speaker_id
    // if (streaming) append streaming; else append format=settings.format
    // append lang=lang ?? settings.lang, length, noise, noisew, segment_size
    // if (model_type == W2V2_VITS) append emotion=settings.dim_emotion
    // else if (model_type == BERT_VITS2) append sdp_ratio, emotion; if text_prompt append; if style_text append + style_weight
    // 非流式：POST /voice/{model_type.toLowerCase()} body=params（form-urlencoded）
    // 流式：return url + "?"+params（URL）— 本差分锁非流式分支（forceNoStreaming=true）
    // modelTypes 常量：VITS='VITS', W2V2_VITS='W2V2-VITS', BERT_VITS2='BERT-VITS2'
    fun vitsForm(
        settings: JsonObject,
        inputText: String,
        voiceId: String,
        forceNoStreaming: Boolean,
    ): JsonObject {
        val streaming = !forceNoStreaming && settings.truthy("streaming")
        val split = voiceId.split("&")
        val modelType = split.getOrNull(0) ?: ""
        val speakerId = split.getOrNull(1) ?: "undefined"
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += "text" to inputText
        pairs += "id" to speakerId
        if (streaming) {
            pairs += "streaming" to "true"
        } else {
            pairs += "format" to settings.primStrOr("format", "")
        }
        pairs += "lang" to settings.primStrOr("lang", "")
        pairs += "length" to settings.numStr("length")
        pairs += "noise" to settings.numStr("noise")
        pairs += "noisew" to settings.numStr("noisew")
        pairs += "segment_size" to settings.numStr("segment_size")
        when (modelType) {
            "W2V2-VITS" -> pairs += "emotion" to settings.numStr("dim_emotion")
            "BERT-VITS2" -> {
                pairs += "sdp_ratio" to settings.numStr("sdp_ratio")
                pairs += "emotion" to settings.numStr("emotion")
                if (settings.truthy("text_prompt")) {
                    pairs += "text_prompt" to settings.primStrOr("text_prompt", "")
                }
                if (settings.truthy("style_text")) {
                    pairs += "style_text" to settings.primStrOr("style_text", "")
                    pairs += "style_weight" to settings.numStr("style_weight")
                }
            }
        }
        return buildJsonObject {
            put("form", JsonPrimitive(pairs.joinToString("&") { (k, v) -> "${formEncode(k)}=${formEncode(v)}" }))
            put("model_type", JsonPrimitive(modelType.lowercase()))
        }
    }

    // ============ XTTS（xtts.js fetchTtsGeneration L289-L320 逐字摘） ============
    // 流式分支：return url（GET），登记不差分
    // 非流式：body = { text: inputText, speaker_wav: voiceId, language: settings.language }
    // 注：processText(text) 在官方定义但 fetchTtsGeneration 不调用 — 不差分 processText（死代码），App 也不调用
    fun xttsBody(settings: JsonObject, inputText: String, voiceId: String): JsonObject = buildJsonObject {
        put("text", JsonPrimitive(inputText))
        put("speaker_wav", JsonPrimitive(voiceId))
        put("language", JsonPrimitive(settings.primStrOr("language", "")))
    }

    // ---------- helpers ----------

    /** JSON.stringify 数字语义：整数值不带小数点（7.0 → 7），小数保留原值。 */
    private fun num(v: Double): JsonPrimitive =
        if (v % 1.0 == 0.0) JsonPrimitive(v.toLong()) else JsonPrimitive(v)

    /** JS `Number(x) || default`：解析失败/0/未定义 → default；非零数字 → x。 */
    private fun JsonObject.numOrDefault(key: String, default: Double): Double {
        val v = this[key] ?: return default
        val s = v.jsonPrimitive.contentOrNull ?: return default
        val n = s.toDoubleOrNull() ?: return default  // Number(non-numeric)=NaN, NaN || default = default
        return if (n == 0.0) default else n
    }

    /** 字符串字段：null/未定义 → default；空串保留空串（JS 字符串读取不兜底）。 */
    private fun JsonObject.strOr(key: String, default: String): String {
        val v = this[key] ?: return default
        return v.jsonPrimitive.contentOrNull ?: default
    }

    /** 布尔字段：null/未定义 → default。 */
    private fun JsonObject.boolOrDefault(key: String, default: Boolean): Boolean {
        val v = this[key] ?: return default
        val s = v.jsonPrimitive.contentOrNull ?: return default
        return when (s.lowercase()) {
            "true" -> true
            "false" -> false
            else -> default
        }
    }

    /** JS `new URL(str)` 成功即合法（用于 google-native reverse_proxy 校验）。 */
    private fun isValidUrl(str: String): Boolean = try {
        java.net.URI(str).toURL()
        true
    } catch (_: Throwable) {
        false
    }

    // ---------- 本地后端 helpers ----------

    /** JS URLSearchParams.toString() 语义：application/x-www-form-urlencoded（space=+）。 */
    private fun formEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    /**
     * 读取 JsonPrimitive 的字面字符串（form 编码用）：
     * - null/未定义 → default（JS settings.x 为 undefined 时 URLSearchParams.append 会变 "undefined"，
     *   由调用方显式传 "undefined"；本函数用于已确认存在的字段）。
     * - 数字/布尔/字符串 → JSON 字面 content（与 JS String(value) 一致：1.0→"1", 0.85→"0.85", false→"false"）。
     */
    private fun JsonObject.primStrOr(key: String, default: String): String {
        val v = this[key] ?: return default
        return v.jsonPrimitive.contentOrNull ?: default
    }

    /**
     * 读取数字字面字符串（form 编码用，对齐 JS `settings.x.toString()`）：
     * - 整数值（1 / 1.0）→ "1"（JS Number.toString 整数不带小数点）。
     * - 小数值（0.85）→ "0.85"。
     * - 字符串字面（"true"/"auto"/路径）→ 原样返回（JS String(x) 恒等）。
     * - null/未定义 → "undefined"（JS String(undefined)="undefined"；URLSearchParams.append 取 string）。
     */
    private fun JsonObject.numStr(key: String): String {
        val v = this[key] ?: return "undefined"
        val s = v.jsonPrimitive.contentOrNull ?: return "undefined"
        // JSON 字面已是 JS toString 语义（1 / 0.85 / false / "auto"）；直接返回。
        // 唯一例外：Kotlin Double.toString 在 App 层构造 JsonObject 时可能产生 "1.0"，
        // 此处归一为 JS Number.toString：整数倍去小数点。
        return s.toDoubleOrNull()?.let { d ->
            if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
        } ?: s
    }

    /** 读取布尔字面字符串（对齐 JS `Boolean.toString()`）：true/false；null/未定义 → "undefined"。 */
    private fun JsonObject.boolStr(key: String): String {
        val v = this[key] ?: return "undefined"
        val s = v.jsonPrimitive.contentOrNull ?: return "undefined"
        // JS Boolean.toString: true→"true", false→"false"；其他字面（如 "true"/"false" 字符串）原样 toString
        return when (s) {
            "true" -> "true"
            "false" -> "false"
            else -> s
        }
    }

    /** JS truthy 判定（if (settings.x)）：false/0/""/null/undefined → false；其余 → true。 */
    private fun JsonObject.truthy(key: String): Boolean {
        val v = this[key] ?: return false
        val s = v.jsonPrimitive.contentOrNull ?: return false
        return when (s) {
            "false", "0", "" -> false
            else -> true
        }
    }

    /** 读取数字 Double（chatterbox JSON body 用）：null/未定义/解析失败 → 0.0（JS undefined→0）。 */
    private fun JsonObject.numOrDouble(key: String): Double {
        val v = this[key] ?: return 0.0
        val s = v.jsonPrimitive.contentOrNull ?: return 0.0
        return s.toDoubleOrNull() ?: 0.0
    }

    /** 读取整数 Long（chatterbox seed / chunk_size 用）：null/未定义/解析失败 → 0。 */
    private fun JsonObject.numOrLong(key: String): Long {
        val v = this[key] ?: return 0L
        val s = v.jsonPrimitive.contentOrNull ?: return 0L
        // JS 数字字面（含 0.85）；整数场景按 toLongOrNull；失败回退 0
        return s.toLongOrNull() ?: s.toDoubleOrNull()?.toLong() ?: 0L
    }

    /** 读取布尔默认 false（chatterbox split_text 用）：null/未定义 → false；"true"→true；其余→false。 */
    private fun JsonObject.boolOrFalse(key: String): Boolean {
        val v = this[key] ?: return false
        val s = v.jsonPrimitive.contentOrNull ?: return false
        return s == "true"
    }

    // ---------- splitRecursive（官方 utils.js L1157-L1189 逐字摘） ----------
    private val DEFAULT_DELIMITERS = listOf("\n\n", "\n", " ", "")

    /**
     * 官方 splitRecursive：按 delimiters 递归切分长文本，再合并短块到 ≤ length。
     * - input=空 → ['']（JS ''.split('\n\n')=['']，结果 ['']）
     * - length<=0 → [input]
     * - 注意 JS `flatParts.flatMap(p => p.length < length ? p : splitRecursive(...))`：
     *   当 p 是字符串时 flatMap 会迭代其字符（'Hel' → ['H','e','l']），导致单字串被铺平。
     *   Kotlin 等价：对短字符串 p 显式 listOf(p)（迭代单个字符串不会拆字符），
     *   但要复刻 JS 把短字符串铺平为字符的行为需用 p.map { it.toString() }。
     *   实际上 JS 短串铺平后由 merge 阶段再合并，最终输出与 listOf(p) 一致（合并回原串），
     *   故此处用 listOf(p) 是安全等价实现。
     */
    fun splitRecursive(input: String, length: Int, delimiters: List<String> = DEFAULT_DELIMITERS): List<String> {
        if (length <= 0) return listOf(input)
        val delim = delimiters.firstOrNull() ?: ""
        // JS input.split(delim)：delim='' 时 JS 把字符串拆为单字符数组（['a','b','c']）。
        // Kotlin String.split('') 不等价（返回原串），需特判：手动拆字符。
        val parts: List<String> = if (delim.isEmpty()) {
            if (input.isEmpty()) listOf("") else input.map { it.toString() }
        } else {
            input.split(delim)
        }
        val flatParts = parts.flatMap { p ->
            if (p.length < length) listOf(p)
            else splitRecursive(p, length, delimiters.drop(1))
        }
        val result = mutableListOf<String>()
        var i = 0
        while (i < flatParts.size) {
            var currentChunk = flatParts[i]
            var j = i + 1
            while (j < flatParts.size) {
                val nextChunk = flatParts[j]
                if (currentChunk.length + nextChunk.length + delim.length <= length) {
                    currentChunk += delim + nextChunk
                } else {
                    break
                }
                j++
            }
            i = j
            result.add(currentChunk)
        }
        return result
    }

    // JsonNull 已通过 import kotlinx.serialization.json.JsonNull 引入
}
