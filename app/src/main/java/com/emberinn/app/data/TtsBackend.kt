package com.emberinn.app.data

import android.content.Context
import com.emberinn.app.ui.settings.VoicePrefs

/**
 * 官方 TTS 扩展 Provider 抽象（public/scripts/extensions/tts/*.js 各 XxxTtsProvider 类）。
 * - getVoices：对齐官方 getVoice(s)，返回该后端可用声音清单（含 lang/preview_url）。
 * - generateTts：对齐官方 generateTts + fetchTtsGeneration，返回音频原始字节（失败 null）。
 * Android 系统 TTS 不走本接口（TtsReader 直连 TextToSpeech），仅外部 HTTP 后端实现。
 */
interface TtsBackend {
    val id: String
    val displayName: String
    /** 是否需要 API Key（设置页提示用）。 */
    val requiresApiKey: Boolean get() = false
    /** 默认端点（可空表示无固定端点，如 google/edge）。 */
    val defaultEndpoint: String get() = ""

    suspend fun getVoices(context: Context): List<TtsVoice>
    suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray?
}

data class TtsVoice(
    val id: String,
    val name: String,
    val lang: String = "en-US",
    val previewUrl: String? = null,
)

/**
 * TTS 后端注册表：对齐官方 tts/index.js 的 provider 分发（按 extension_settings.tts.provider 切换）。
 * 用法：TtsBackendRegistry.get(provider) ?: 走 Android 系统 TTS 回退。
 */
object TtsBackendRegistry {
    private val backends = LinkedHashMap<String, TtsBackend>()

    init {
        // 触发三个 Init 对象类加载 → 注册全部 27 个后端
        // （Kotlin object 懒加载，首次访问本 Registry 时执行；此时 backends 已初始化）
        TtsBackendsCloudInit.javaClass
        TtsBackendsLocal1Init.javaClass
        TtsBackendsLocal2Init.javaClass
    }

    fun register(b: TtsBackend) { backends[b.id] = b }
    fun get(id: String): TtsBackend? = backends[id]
    fun all(): List<TtsBackend> = backends.values.toList()
    fun ids(): List<String> = backends.keys.toList()

    /** 当前用户选定的后端（VoicePrefs.ttsProvider）；null 表示走 Android 系统 TTS。 */
    fun current(context: Context): TtsBackend? {
        val p = VoicePrefs.ttsProvider(context)
        return if (p.isBlank() || p == "system") null else get(p)
    }
}
