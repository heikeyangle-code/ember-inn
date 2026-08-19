package com.emberinn.app.data

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import com.emberinn.app.ui.settings.VoicePrefs
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 聊天朗读执行层（P1-6）：
 * - Android 系统 TTS（provider="system"）：对齐官方 tts 扩展 index.js 文本处理（skip_codeblocks/skip_tags/
 *   pass_asterisks=false/apply_regex/移除内嵌图片/narrate_by_paragraphs 按行排队）。
 * - 外部 HTTP 后端（provider!=system）：调 TtsBackendRegistry.current.generateTts 取音频字节 →
 *   落盘临时文件 → MediaPlayer 播放（对齐官方 AudioElement.src = blob 后 new Audio() 播放）。
 * 近似登记：官方先 substituteParams 宏替换，本实现由调用方（ChatViewModel.narrateText）完成；
 * 多语音/对话专属/引号专属（multi_voice_enabled + voiceMap 三段）暂不实现。
 */
object TtsReader {

    private var tts: TextToSpeech? = null
    private var ready = false
    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    @Synchronized
    private fun engine(context: Context): TextToSpeech? {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
            }
        }
        return tts
    }

    /** Android 系统 TTS 朗读（narrate_by_paragraphs 时按行排队，否则整段一次）；返回是否成功发起。 */
    @Synchronized
    fun speakSystem(
        context: Context,
        text: String,
        voice: String,
        rate: Float,
        byParagraphs: Boolean,
    ): Boolean {
        if (text.isBlank()) return false
        val engine = engine(context) ?: return false
        if (!ready) return false
        val selected = engine.voices?.firstOrNull { it.name == voice }
        if (selected != null && engine.setVoice(selected) == TextToSpeech.ERROR) {
            engine.setLanguage(selected.locale)
        }
        engine.setSpeechRate(rate)
        val lines = if (byParagraphs) text.split('\n').filter { it.isNotBlank() } else listOf(text)
        if (lines.isEmpty()) return false
        engine.speak(lines.first(), TextToSpeech.QUEUE_FLUSH, null, "ember_tts")
        lines.drop(1).forEach { line ->
            engine.speak(line, TextToSpeech.QUEUE_ADD, null, "ember_tts")
        }
        return true
    }

    /**
     * 外部后端朗读：调 TtsBackend.generateTts 取字节 → 落盘 → MediaPlayer 播放。
     * 返回是否成功发起（字节取得且 MediaPlayer 开始播放）。
     */
    suspend fun speakExternal(context: Context, text: String, voiceId: String): Boolean = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext false
        val backend = TtsBackendRegistry.current(context) ?: return@withContext false
        val bytes = runCatching { backend.generateTts(context, text, voiceId) }.getOrNull() ?: return@withContext false
        if (bytes.isEmpty()) return@withContext false
        stopExternal()
        val dir = File(context.cacheDir, "tts").apply { mkdirs() }
        val file = File(dir, "tts-${System.nanoTime()}.mp3")
        file.writeBytes(bytes)
        try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                mp.release()
                file.delete()
                if (mediaPlayer === mp) mediaPlayer = null
            }
            mp.setOnErrorListener { _, _, _ ->
                mp.release(); file.delete()
                if (mediaPlayer === mp) mediaPlayer = null
                true
            }
            mp.prepare()
            mp.start()
            mediaPlayer = mp
            true
        } catch (e: Exception) {
            file.delete()
            false
        }
    }

    /** 主入口：按 VoicePrefs.ttsProvider 分流（system → Android，其他 → 外部后端）。 */
    suspend fun speak(
        context: Context,
        text: String,
        voice: String,
        rate: Float,
        byParagraphs: Boolean,
    ): Boolean {
        val provider = VoicePrefs.ttsProvider(context)
        return if (provider.isBlank() || provider == "system") {
            speakSystem(context, text, voice, rate, byParagraphs)
        } else {
            // 外部后端忽略 rate/byParagraphs（后端自身处理；分段朗读由调用方循环调本方法）
            val segments = if (byParagraphs) text.split('\n').filter { it.isNotBlank() } else listOf(text)
            var ok = false
            for (seg in segments) {
                ok = speakExternal(context, seg, voice) || ok
            }
            ok
        }
    }

    @Synchronized
    fun stop() {
        tts?.stop()
        stopExternal()
    }

    @Synchronized
    private fun stopExternal() {
        mediaPlayer?.let { runCatching { it.stop(); it.release() } }
        mediaPlayer = null
    }

    @Synchronized
    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
        stopExternal()
    }
}
