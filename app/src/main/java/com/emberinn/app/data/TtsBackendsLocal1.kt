package com.emberinn.app.data

import android.content.Context
import com.emberinn.app.ui.settings.VoicePrefs
import com.emberinn.engine.prompt.TtsRequestEngine
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 第一批本地 TTS 后端：准则 2——引擎层 [TtsRequestEngine] 构造 .js body（差分 38 例），
 * App 层只做：①构建 settings JsonObject（含官方 defaultSettings + VoicePrefs 覆盖）；
 * ②调引擎层得 body；③拼 URL + Header + 发请求 + 解析响应。共用文件级 OkHttpClient
 * （connect 15s / read 120s）；generateTts 失败统一返回 null。路由分发由 TtsBackendRegistry
 * （见 TtsBackend.kt）按 provider id 处理。
 *
 * 注：
 * - alltalk.js 用 URLSearchParams（form-urlencoded）且返回 JSON {output_file_url}（非直接音频字节），
 *   忠实保留 form 方式 + 二次 GET 取音频；未强行改写成 JSON。
 * - gsvi.js fetchTtsGeneration 用 URLSearchParams 拼 GET URL（无 body），忠实保留。
 * - kokoro.js 为浏览器 WebWorker（无 fetchTtsGeneration、无 HTTP），与官方不同源——
 *   按需求契约为 POST {endpoint}/tts {text,voice}（登记不差分，见 HANDOFF 4.4）。
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

/** JsonObject → org.json.JSONObject（用于 OkHttp post body）。 */
private fun JsonObject.toOrgJson(): org.json.JSONObject {
    val out = org.json.JSONObject()
    for (k in keys) {
        val v = this[k] ?: continue
        when {
            v is JsonObject -> out.put(k, org.json.JSONObject(v.toString()))
            else -> {
                val prim = v.jsonPrimitive
                val s = prim.content
                when {
                    s == "true" || s == "false" -> out.put(k, s.toBoolean())
                    s.toDoubleOrNull() != null -> out.put(k, s.toDouble())
                    else -> out.put(k, s)
                }
            }
        }
    }
    return out
}

// 1) alltalk — alltalk.js fetchTtsGeneration：POST {endpoint}/api/tts-generate，
//    Content-Type application/x-www-form-urlencoded + Cache-Control no-cache，
//    body 由引擎层 [TtsRequestEngine.allTalkForm] 构造（form-urlencoded，与官方 URLSearchParams 一致）。
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
                // 官方 defaultSettings（constructor this.settings）
                val settings = buildJsonObject {
                    put("server_version", JsonPrimitive("v2"))
                    put("language", JsonPrimitive("en"))
                    put("narrator_enabled", JsonPrimitive("false"))
                    put("at_narrator_text_not_inside", JsonPrimitive("narrator"))
                    put("narrator_voice_gen", JsonPrimitive("Please set a voice"))
                    put("rvc_character_voice", JsonPrimitive("Disabled"))
                    put("rvc_character_pitch", JsonPrimitive("0"))
                    put("rvc_narrator_voice", JsonPrimitive("Disabled"))
                    put("rvc_narrator_pitch", JsonPrimitive("0"))
                }
                val form = TtsRequestEngine.allTalkForm(settings, text, voiceId)
                    .jsonObject["form"]!!.jsonPrimitive.content
                val genReq = Request.Builder()
                    .url("$base/api/tts-generate")
                    .post(form.toRequestBody(formMedia))
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
//    POST {endpoint}/tts，Content-Type application/json + Cache-Control no-cache。
//    body 由引擎层 [TtsRequestEngine.chatterboxBody] 构造。
//    voiceId 以 'ref_' 前缀 → clone 模式；否则 predefined。seed 默认 -1 → 随机（App 传 Random）。
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
                // 官方 defaultSettings（constructor this.settings）
                val settings = buildJsonObject {
                    put("temperature", JsonPrimitive(0.8))
                    put("exaggeration", JsonPrimitive(0.5))
                    put("cfg_weight", JsonPrimitive(0.5))
                    put("seed", JsonPrimitive(-1))
                    put("speed_factor", JsonPrimitive(1.0))
                    put("language", JsonPrimitive("en"))
                    put("split_text", JsonPrimitive(true))
                    put("chunk_size", JsonPrimitive(120))
                    put("output_format", JsonPrimitive("wav"))
                    put("predefined_voice", JsonPrimitive("S1"))
                }
                val body = TtsRequestEngine.chatterboxBody(
                    settings, text, voiceId,
                    // JS Math.floor(Math.random()*2147483648) → 0..2^31-1
                    randomSeed = Random.nextLong(0, 2147483648L),
                )
                val request = Request.Builder()
                    .url("$base/tts")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 3) coqui — coqui.js 无独立 fetchTtsGeneration（生成逻辑内联在 generateTts），且走 ST Extras 代理
//    /api/text-to-speech/coqui/generate-tts。body 由引擎层 [TtsRequestEngine.coquiBody] 构造。
//    voiceId 形如 'tts_models/.../model[lang][speaker]'：tokens 解析逻辑在引擎层。
//    language/speaker 为 'none' → parseInt('none')=NaN → JSON null。
//    注：coqui 走 ST Extras 代理（getApiUrl），官方默认 http://localhost:5100（extensions.js:62）。
class CoquiTtsBackend : TtsBackend {
    override val id = "coqui"
    override val displayName = "Coqui TTS"
    override val defaultEndpoint = "http://localhost:5100"

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
                // App 无 customVoices（官方 voiceMapDict）→ 恒等映射，传空 settings
                val body = TtsRequestEngine.coquiBody(buildJsonObject {}, text, voiceId)
                val request = Request.Builder()
                    .url("$base/api/text-to-speech/coqui/generate-tts")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 4) cosyvoice — cosyvoice.js fetchTtsGeneration：POST {endpoint}/（根路径），Content-Type application/json，
//    body 由引擎层 [TtsRequestEngine.cosyVoiceBody] 构造。默认 streaming=false → 不带 streaming 字段。
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
                val settings = buildJsonObject {
                    put("streaming", JsonPrimitive(false))
                }
                val body = TtsRequestEngine.cosyVoiceBody(settings, text, voiceId)
                val request = Request.Builder()
                    .url("$base/")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 5) gpt-sovits-adapter — gpt-sovits-adapter.js fetchTtsGeneration：POST {endpoint}/（根路径），
//    Content-Type application/json，body 由引擎层 [TtsRequestEngine.gptSoVitsAdapterBody] 构造。
//    card_name 官方取 getCharacters(false)，Android 无角色上下文 → 空串（引擎层打桩）。
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
                val settings = buildJsonObject {
                    put("text_lang", JsonPrimitive("zh"))
                    put("media_type", JsonPrimitive("auto"))
                }
                val body = TtsRequestEngine.gptSoVitsAdapterBody(settings, text, voiceId)
                val request = Request.Builder()
                    .url("$base/")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 6) gpt-sovits-v2 — gpt-sovits-v2.js fetchTtsGeneration：POST {endpoint}/（根路径），
//    Content-Type application/json，body 由引擎层 [TtsRequestEngine.gptSoVitsV2Body] 构造。
//    prompt_text = replaceSpeaker(voiceId)（去 [..]）；ref_audio_path = './参考音频/'+voiceId+'.wav'（引擎层）。
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
                val settings = buildJsonObject {
                    put("text_lang", JsonPrimitive("zh"))
                    put("prompt_lang", JsonPrimitive("zh"))
                }
                val body = TtsRequestEngine.gptSoVitsV2Body(settings, text, voiceId)
                val request = Request.Builder()
                    .url("$base/")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 7) gsvi — gsvi.js fetchTtsGeneration：构造 GET URL ${endpoint}/tts?{query}（URLSearchParams，无 body），
//    query 由引擎层 [TtsRequestEngine.gsviQuery] 构造。返回该 URL 字符串；官方 generateTts 再 fetch 取音频。
//    此处直接 GET 该 URL 取字节。
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
                // 官方 defaultSettings
                val settings = buildJsonObject {
                    put("language", JsonPrimitive("多语种混合"))
                    put("batch_size", JsonPrimitive(10))
                    put("speed", JsonPrimitive(1))
                    put("top_k", JsonPrimitive(6))
                    put("top_p", JsonPrimitive(0.85))
                    put("temperature", JsonPrimitive(0.75))
                    put("stream", JsonPrimitive(false))
                }
                val query = TtsRequestEngine.gsviQuery(settings, text, voiceId)
                    .jsonObject["query"]!!.jsonPrimitive.content
                val request = Request.Builder()
                    .url("$base/tts?$query")
                    .get()
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 8) kokoro — kokoro.js 为浏览器 WebWorker 实现（无 fetchTtsGeneration、无 HTTP 请求，generateTts 经
//    worker.postMessage 发 {text, voice, speakingRate, requestId}）。Android 无 Worker；与官方不同源——
//    按需求契约为 POST {endpoint}/tts，JSON {text, voice}（登记不差分，见 HANDOFF 4.4）。
//    voice 默认 'af_heart'（settings.defaultVoice）。注：kokoro.js 无 HTTP 端点默认，用 kokoro HTTP 服务常用端口作默认。
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
                // 与官方不同源（WebWorker vs HTTP），App 简化为 {text, voice} JSON body
                val body = JSONObject()
                    .put("text", text)
                    .put("voice", voiceId.ifBlank { "af_heart" })
                    .toString()
                val request = Request.Builder()
                    .url("$base/tts")
                    .post(body.toRequestBody(jsonMedia))
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
