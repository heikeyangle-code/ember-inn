package com.emberinn.app.data

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 聊天朗读执行层（P1-6）：Android 系统 TTS，文本处理对齐官方 tts 扩展 index.js：
 * - skip_codeblocks：移除 ```...``` / ~~~...~~~ 块
 * - skip_tags：移除 <tag>...</tag>
 * - pass_asterisks=false（官方默认）：移除 * 字符
 * - apply_regex：按用户正则移除并折叠空白
 * - 移除内嵌图片 ![...](...)
 * - narrate_by_paragraphs：按行排队朗读（官方按 \n 分段入队）
 * 近似登记：官方先 substituteParams 宏替换，本实现不替换（聊天正文一般已无宏）；
 * 多语音/对话专属/引号专属等字段设置页未暴露，暂不实现。
 */
object TtsReader {

    private var tts: TextToSpeech? = null
    private var ready = false

    @Synchronized
    private fun engine(context: Context): TextToSpeech? {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
            }
        }
        return tts
    }

    /** 朗读（narrate_by_paragraphs 时按行排队，否则整段一次）；返回是否成功发起。 */
    @Synchronized
    fun speak(
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

    @Synchronized
    fun stop() {
        tts?.stop()
    }

    @Synchronized
    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
