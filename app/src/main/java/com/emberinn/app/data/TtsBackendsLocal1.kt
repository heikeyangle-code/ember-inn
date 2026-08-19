package com.emberinn.app.data

import android.content.Context
import com.emberinn.app.ui.settings.VoicePrefs
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 第一批本地 TTS 后端：1:1 对照官方 public/scripts/extensions/tts/{后端}.js 的 fetchTtsGeneration
 * （无 fetchTtsGeneration 者对照 generateTts 内联生成请求）翻译成 Kotlin。共用文件级 OkHttpClient
 * （connect 15s / read 120s）；generateTts 失败统一返回 null。路由分发由 TtsBackendRegistry
 * （见 TtsBackend.kt）按 provider id 处理。
 *
 * 注：
 * - alltalk.js 用 URLSearchParams（form-urlencoded）且返回 JSON {output_file_url}（非直接音频字节），
 *   忠实保留 form 方式 + 二次 GET 取音频；未强行改写成 JSON。
 * - gsvi.js fetchTtsGeneration 用 URLSearchParams 拼 GET URL（无 body），忠实保留。
 * - kokoro.js 为浏览器 WebWorker（无 fetchTtsGeneration、无 HTTP），按需求契约为 POST {endpoint}/tts
 *   {text,voice}（与 TtsBackendsLocal2.kt 的 kokoro-worker 同源处理思路）。
 * - 默认端点取各 .js 的官方默认值（defaultSettings / constructor settings.provider_endpoint）。
 */

private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

private val jsonMedia = "application/json; charset=utf-8".toMediaType()
private val formMedia = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()

private fun endpoint(context: Context, fallback: String): String =
    VoicePrefs.ttsEndpoint(context).trim().ifBlank { fallback }

private fun enc(v: Any): String = URLEncoder.encode(v.toString(), "UTF-8")

// 1) alltalk — alltalk.js fetchTtsGeneration：POST {endpoint}/api/tts-generate，
//    Content-Type application/x-www-form-urlencoded + Cache-Control no-cache，
//    body = URLSearchParams（text_input, text_filtering='standard', character_voice_gen=voiceId,
//    narrator_enabled, narrator_voice_gen, text_not_inside, language, output_file_name='st_output',
//    output_file_timestamp='true', autoplay='false', autoplay_volume='0.8'；V2 RVC 可选项省略）。
//    返回 JSON {output_file_url}（V1 完整 URL / V2 相对路径），官方 generateTts 再 fetch 该 URL 取音频字节。
//    官方默认 provider_endpoint=http://localhost:7851。
class AllTalkTtsBackend : TtsBackend {
    override val id = "alltalk"
    override val displayName = "AllTalk"
    override val defaultEndpoint = "http://localhost:7851"

    // alltalk.js 无静态 voice 列表（运行时 GET /api/voices 拉取服务器声音文件名）。
    override suspend fun getVoices(context: Context): List<TtsVoice> = emptyList()

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = endpoint(context, defaultEndpoint).trimEnd('/')
                // 官方 settings 默认值（constructor this.settings）
                val params = listOf(
                    "text_input" to text,
                    "text_filtering" to "standard",
                    "character_voice_gen" to voiceId,
                    "narrator_enabled" to "false",
                    "narrator_voice_gen" to "Please set a voice",
                    "text_not_inside" to "narrator",
                    "language" to "en",
                    "output_file_name" to "st_output",
                    "output_file_timestamp" to "true",
                    "autoplay" to "false",
                    "autoplay_volume" to "0.8",
                )
                val formBody = params.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
                val genReq = Request.Builder()
                    .url("$base/api/tts-generate")
                    .post(formBody.toRequestBody(formMedia))
                    .header("Cache-Control", "no-cache")
                    .build()
                // V1 返回完整 URL；V2 返回相对路径 → 拼 endpoint
                val audioUrl: String? = client.newCall(genReq).execute().use { resp ->
                    if (!resp.isSuccessful) null
                    else JSONObject(resp.body?.string().orEmpty())
                        .optString("output_file_url")
                        .takeIf { it.isNotBlank() }
                        ?.let { rel -> if (rel.startsWith("http")) rel else "$base$rel" }
                }
                val audioReq = audioUrl?.let { Request.Builder().url(it).get().build() }
                if (audioReq == null) null
                else client.newCall(audioReq).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 2) chatterbox — chatterbox.js 无独立 fetchTtsGeneration（生成逻辑内联在 generateTts）：
//    POST {endpoint}/tts，Content-Type application/json + Cache-Control no-cache，
//    body = {text, voice_mode, temperature, exaggeration, cfg_weight, seed, speed_factor, language,
//    split_text, chunk_size, output_format, predefined_voice_id | reference_audio_filename}。
//    voiceId 以 'ref_' 前缀 → clone 模式（reference_audio_filename）；否则 predefined（predefined_voice_id）。
//    seed 默认 -1 → 随机（官方 Math.floor(Math.random()*2147483648)）。返回音频字节（response 即音频）。
//    官方默认 provider_endpoint=http://localhost:8004，predefined_voice='S1'。
class ChatterboxTtsBackend : TtsBackend {
    override val id = "chatterbox"
    override val displayName = "Chatterbox"
    override val defaultEndpoint = "http://localhost:8004"

    // chatterbox.js 无静态 voice 列表（运行时 GET /get_predefined_voices）。默认 predefined_voice='S1'。
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("S1", "S1", "en-US"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = endpoint(context, defaultEndpoint).trimEnd('/')
                val isRef = voiceId.startsWith("ref_")
                val actual = if (isRef) voiceId.removePrefix("ref_") else voiceId
                // 官方 settings 默认值（constructor this.settings）
                val json = JSONObject()
                    .put("text", text)
                    .put("voice_mode", if (isRef) "clone" else "predefined")
                    .put("temperature", 0.8)
                    .put("exaggeration", 0.5)
                    .put("cfg_weight", 0.5)
                    .put("seed", Random.nextInt(0, 2147483647))
                    .put("speed_factor", 1.0)
                    .put("language", "en")
                    .put("split_text", true)
                    .put("chunk_size", 120)
                    .put("output_format", "wav")
                if (isRef) {
                    json.put("reference_audio_filename", actual)
                } else {
                    json.put("predefined_voice_id", actual.ifBlank { "S1" })
                }
                val request = Request.Builder()
                    .url("$base/tts")
                    .post(json.toString().toRequestBody(jsonMedia))
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 3) coqui — coqui.js 无独立 fetchTtsGeneration（生成逻辑内联在 generateTts），且走 ST Extras 代理
//    /api/text-to-speech/coqui/generate-tts。body = {text, model_id, language_id, speaker_id}。
//    voiceId 形如 'tts_models/.../model[lang][speaker]'：replaceAll(']','').replaceAll('"','').split('[')，
//    tokens[0]=model_id；tokens[1] 若 model_id 含 'multilingual' 为 language 否则 speaker；tokens[2]=speaker。
//    language/speaker 为 'none' → parseInt('none')=NaN → JSON null。返回音频字节。
//    注：coqui.js 无 provider_endpoint 默认（走 getApiUrl），此处用 Coqui API 常用端口作本地默认。
class CoquiTtsBackend : TtsBackend {
    override val id = "coqui"
    override val displayName = "Coqui TTS"
    override val defaultEndpoint = "http://localhost:5002"

    // coqui.js fetchTtsVoiceObjects 返回用户 voiceMapDict（无静态列表）。给出 .js 注释中的示例 model_id 作占位。
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("tts_models/en/ljspeech/glow-tts", "Glow-TTS (en)", "en-US"),
        TtsVoice("tts_models/en/ljspeech/tacotron2-DDC", "Tacotron2-DDC (en)", "en-US"),
        TtsVoice("tts_models/multilingual/multi-dataset/your_tts", "Your TTS (multilingual)", "en-US"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = endpoint(context, defaultEndpoint).trimEnd('/')
                // 官方 voiceId 解析逻辑（generateTts 内）
                val cleaned = voiceId.replace("]", "").replace("\"", "")
                val tokens = cleaned.split("[")
                val modelId = tokens[0]
                var language = "none"
                var speaker = "none"
                if (tokens.size > 1) {
                    val o = tokens[1]
                    if (modelId.contains("multilingual")) language = o else speaker = o
                }
                if (tokens.size > 2) speaker = tokens[2]
                val json = JSONObject()
                    .put("text", text)
                    .put("model_id", modelId)
                    .put("language_id", language.toIntOrNull() ?: JSONObject.NULL)
                    .put("speaker_id", speaker.toIntOrNull() ?: JSONObject.NULL)
                val request = Request.Builder()
                    .url("$base/api/text-to-speech/coqui/generate-tts")
                    .post(json.toString().toRequestBody(jsonMedia))
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 4) cosyvoice — cosyvoice.js fetchTtsGeneration：POST {endpoint}/（根路径），Content-Type application/json，
//    body = {text, speaker: voiceId, [streaming:1]}。默认 streaming=false → 不带 streaming 字段。返回音频字节。
//    官方默认 provider_endpoint=http://localhost:9880（defaultSettings.provider_endpoint）。
class CosyVoiceTtsBackend : TtsBackend {
    override val id = "cosyvoice"
    override val displayName = "CosyVoice"
    override val defaultEndpoint = "http://localhost:9880"

    // cosyvoice.js 通过 GET /speakers 拉取（无静态 voice 列表）。
    override suspend fun getVoices(context: Context): List<TtsVoice> = emptyList()

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = endpoint(context, defaultEndpoint).trimEnd('/')
                val json = JSONObject()
                    .put("text", text)
                    .put("speaker", voiceId)
                val request = Request.Builder()
                    .url("$base/")
                    .post(json.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 5) gpt-sovits-adapter — gpt-sovits-adapter.js fetchTtsGeneration：POST {endpoint}/（根路径），
//    Content-Type application/json，body = {text, card_name, use_st_adapter:true, target_voice: voiceId,
//    text_lang, text_split_method:'cut5', batch_size:1, media_type, streaming_mode:'true'}。
//    card_name 官方取 getCharacters(false)，Android 无角色上下文 → 空串。返回音频字节。
//    官方默认 provider_endpoint=http://localhost:9881, text_lang='zh', media_type='auto'。
class GptSoVitsAdapterTtsBackend : TtsBackend {
    override val id = "gpt-sovits-adapter"
    override val displayName = "GPT-SoVITS (Adapter)"
    override val defaultEndpoint = "http://localhost:9881"

    // gpt-sovits-adapter.js 通过 GET /speakers 拉取（无静态 voice 列表）。
    override suspend fun getVoices(context: Context): List<TtsVoice> = emptyList()

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = endpoint(context, defaultEndpoint).trimEnd('/')
                val json = JSONObject()
                    .put("text", text)
                    .put("card_name", "")
                    .put("use_st_adapter", true)
                    .put("target_voice", voiceId)
                    .put("text_lang", "zh")
                    .put("text_split_method", "cut5")
                    .put("batch_size", 1)
                    .put("media_type", "auto")
                    .put("streaming_mode", "true")
                val request = Request.Builder()
                    .url("$base/")
                    .post(json.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 6) gpt-sovits-v2 — gpt-sovits-v2.js fetchTtsGeneration：POST {endpoint}/（根路径），
//    Content-Type application/json，body = {text, prompt_text, ref_audio_path, text_lang, prompt_lang,
//    text_split_method:'cut5', batch_size:1, media_type:'ogg', streaming_mode:'true'}。
//    prompt_text = replaceSpeaker(voiceId)（去 [..]）；ref_audio_path = './参考音频/'+voiceId+'.wav'。
//    官方默认 provider_endpoint=http://localhost:9880, text_lang='zh', prompt_lang='zh'。
class GptSoVitsV2TtsBackend : TtsBackend {
    override val id = "gpt-sovits-v2"
    override val displayName = "GPT-SoVITS V2"
    override val defaultEndpoint = "http://localhost:9880"

    // gpt-sovits-v2.js 通过 GET /speakers 拉取（无静态 voice 列表）。
    override suspend fun getVoices(context: Context): List<TtsVoice> = emptyList()

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = endpoint(context, defaultEndpoint).trimEnd('/')
                // 官方 replaceSpeaker：text.replace(/\[.*?\]/gu, '')
                val promptText = voiceId.replace(Regex("""\[.*?\]"""), "")
                val json = JSONObject()
                    .put("text", text)
                    .put("prompt_text", promptText)
                    .put("ref_audio_path", "./参考音频/$voiceId.wav")
                    .put("text_lang", "zh")
                    .put("prompt_lang", "zh")
                    .put("text_split_method", "cut5")
                    .put("batch_size", 1)
                    .put("media_type", "ogg")
                    .put("streaming_mode", "true")
                val request = Request.Builder()
                    .url("$base/")
                    .post(json.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 7) gsvi — gsvi.js fetchTtsGeneration：构造 GET URL ${endpoint}/tts?{query}（URLSearchParams，无 body），
//    字段 text, cha_name=voiceId, text_language, batch_size, speed, top_k, top_p, temperature, stream。
//    返回该 URL 字符串；官方 generateTts 再 fetch 取音频。此处直接 GET 该 URL 取字节。返回音频字节。
//    官方默认 provider_endpoint=http://127.0.0.1:5000, language='多语种混合', speed=1, top_k=6, top_p=0.85,
//    temperature=0.75, batch_size=10, stream=false。
class GsviTtsBackend : TtsBackend {
    override val id = "gsvi"
    override val displayName = "GSVI"
    override val defaultEndpoint = "http://127.0.0.1:5000"

    // gsvi.js 通过 GET /character_list 拉取角色（无静态 voice 列表）。
    override suspend fun getVoices(context: Context): List<TtsVoice> = emptyList()

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = endpoint(context, defaultEndpoint).trimEnd('/')
                val params = listOf(
                    "text" to text,
                    "cha_name" to voiceId,
                    "text_language" to "多语种混合",
                    "batch_size" to 10,
                    "speed" to 1,
                    "top_k" to 6,
                    "top_p" to 0.85,
                    "temperature" to 0.75,
                    "stream" to false,
                )
                val query = params.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
                val request = Request.Builder()
                    .url("$base/tts?$query")
                    .get()
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 8) kokoro — kokoro.js 为浏览器 WebWorker 实现（无 fetchTtsGeneration、无 HTTP 请求，generateTts 经
//    worker.postMessage 发 {text, voice, speakingRate, requestId}）。Android 无 Worker；按需求契约为
//    POST {endpoint}/tts，JSON {text, voice}。voice 默认 'af_heart'（settings.defaultVoice）。返回音频字节。
//    注：kokoro.js 无 HTTP 端点默认，用 kokoro HTTP 服务常用端口作默认。
class KokoroTtsBackend : TtsBackend {
    override val id = "kokoro"
    override val displayName = "Kokoro"
    override val defaultEndpoint = "http://localhost:8880"

    // kokoro.js 构造函数内 this.voices 静态 28 个（逐字 copy），lang 按 'b' 前缀判 en-GB/en-US。
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("af_heart", "af_heart", "en-US"),
        TtsVoice("af_alloy", "af_alloy", "en-US"),
        TtsVoice("af_aoede", "af_aoede", "en-US"),
        TtsVoice("af_bella", "af_bella", "en-US"),
        TtsVoice("af_jessica", "af_jessica", "en-US"),
        TtsVoice("af_kore", "af_kore", "en-US"),
        TtsVoice("af_nicole", "af_nicole", "en-US"),
        TtsVoice("af_nova", "af_nova", "en-US"),
        TtsVoice("af_river", "af_river", "en-US"),
        TtsVoice("af_sarah", "af_sarah", "en-US"),
        TtsVoice("af_sky", "af_sky", "en-US"),
        TtsVoice("am_adam", "am_adam", "en-US"),
        TtsVoice("am_echo", "am_echo", "en-US"),
        TtsVoice("am_eric", "am_eric", "en-US"),
        TtsVoice("am_fenrir", "am_fenrir", "en-US"),
        TtsVoice("am_liam", "am_liam", "en-US"),
        TtsVoice("am_michael", "am_michael", "en-US"),
        TtsVoice("am_onyx", "am_onyx", "en-US"),
        TtsVoice("am_puck", "am_puck", "en-US"),
        TtsVoice("am_santa", "am_santa", "en-US"),
        TtsVoice("bf_emma", "bf_emma", "en-GB"),
        TtsVoice("bf_isabella", "bf_isabella", "en-GB"),
        TtsVoice("bm_george", "bm_george", "en-GB"),
        TtsVoice("bm_lewis", "bm_lewis", "en-GB"),
        TtsVoice("bf_alice", "bf_alice", "en-GB"),
        TtsVoice("bf_lily", "bf_lily", "en-GB"),
        TtsVoice("bm_daniel", "bm_daniel", "en-GB"),
        TtsVoice("bm_fable", "bm_fable", "en-GB"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = endpoint(context, defaultEndpoint).trimEnd('/')
                val json = JSONObject()
                    .put("text", text)
                    .put("voice", voiceId.ifBlank { "af_heart" })
                val request = Request.Builder()
                    .url("$base/tts")
                    .post(json.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

/** 注册第一批 8 个本地 TTS 后端。在 App 启动时被引用即触发 init。 */
object TtsBackendsLocal1Init {
    init {
        TtsBackendRegistry.register(AllTalkTtsBackend())
        TtsBackendRegistry.register(ChatterboxTtsBackend())
        TtsBackendRegistry.register(CoquiTtsBackend())
        TtsBackendRegistry.register(CosyVoiceTtsBackend())
        TtsBackendRegistry.register(GptSoVitsAdapterTtsBackend())
        TtsBackendRegistry.register(GptSoVitsV2TtsBackend())
        TtsBackendRegistry.register(GsviTtsBackend())
        TtsBackendRegistry.register(KokoroTtsBackend())
    }
}
