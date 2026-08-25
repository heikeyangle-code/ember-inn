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
    /** 官方 ttsJobQueue 串行语义（tts/index.js:476-535）：上一段 onCompletion 再起下一段。 */
    private fun playSequence(files: List<File>, idx: Int, rate: Float): Boolean {
        if (idx >= files.size) return true
        val file = files[idx]
        return try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                mp.release()
                file.delete()
                if (mediaPlayer === mp) mediaPlayer = null
                if (idx + 1 < files.size) playSequence(files, idx + 1, rate)
            }
            mp.setOnErrorListener { _, _, _ ->
                mp.release(); file.delete()
                if (mediaPlayer === mp) mediaPlayer = null
                true
            }
            mp.prepare()
            // 官方 playback_rate 在 canplay 应用（index.js:361）；此前登记"语速对外部不生效"
            if (rate > 0f && rate != 1f) {
                runCatching { mp.playbackParams = mp.playbackParams.setSpeed(rate) }
            }
            mp.start()
            mediaPlayer = mp
            true
        } catch (e: Exception) {
            file.delete()
            playSequence(files, idx + 1, rate) // 单段坏文件跳过继续（官方失败 toastr 后队列续行）
        }
    }

    /**
     * 外部后端朗读：调 TtsBackend.generateTts 取字节 → 落盘 → MediaPlayer 播放。
     * 返回是否成功发起（至少一段取得字节并开始播放）。
     */
    suspend fun speakExternal(
        context: Context,
        text: String,
        voiceId: String,
        rate: Float = 1f,
        byParagraphs: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext false
        val backend = TtsBackendRegistry.current(context) ?: return@withContext false
        stopExternal()
        val dir = File(context.cacheDir, "tts").apply { mkdirs() }
        // 分段全部下载完再串行播——原先边下边播且每段先 stop，前一段起播即被下一段杀掉（只剩末段出声）
        val files = mutableListOf<File>()
        val segments = if (byParagraphs) text.split('\n').filter { it.isNotBlank() } else listOf(text)
        for (seg in segments) {
            val bytes = runCatching { backend.generateTts(context, seg, voiceId) }.getOrNull() ?: continue
            if (bytes.isEmpty()) continue
            files.add(File(dir, "tts-${System.nanoTime()}.mp3").apply { writeBytes(bytes) })
        }
        if (files.isEmpty()) return@withContext false
        playSequence(files, 0, rate)
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
            // 外部后端：分段下载+串行播放（narrate_by_paragraphs 才按行拆）；语速经 playbackParams 生效
            speakExternal(context, text, voice, rate, byParagraphs)
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
