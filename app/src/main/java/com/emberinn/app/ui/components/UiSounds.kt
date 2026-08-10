package com.emberinn.app.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.emberinn.app.ui.settings.AppearancePrefs
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * README UI 质感清单 6：极短 UI 反馈音。
 * 运行时合成三段正弦衰减音（发送 / 切换 / 删除），SoundPool 预加载，
 * USAGE_ASSISTANCE_SONIFICATION 语义；AppearancePrefs.uiSounds 可整体关闭。
 */
object UiSounds {

    @Volatile private var pool: SoundPool? = null
    @Volatile private var sendId = 0
    @Volatile private var toggleId = 0
    @Volatile private var deleteId = 0
    @Volatile private var ready = false

    fun ensure(context: Context) {
        if (pool != null) return
        synchronized(this) {
            if (pool != null) return
            runCatching {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val p = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build()
                val dir = File(context.cacheDir, "ui_sounds").apply { mkdirs() }
                sendId = p.load(writeWav(File(dir, "send.wav"), 880f, 90), 1)
                toggleId = p.load(writeWav(File(dir, "toggle.wav"), 1320f, 55), 1)
                deleteId = p.load(writeWav(File(dir, "delete.wav"), 392f, 140), 1)
                p.setOnLoadCompleteListener { _, _, _ -> ready = true }
                pool = p
            }
        }
    }

    fun send(context: Context) = play(context, sendId)
    fun toggle(context: Context, on: Boolean) = play(context, toggleId)
    fun delete(context: Context) = play(context, deleteId)

    private fun play(context: Context, soundId: Int) {
        if (!AppearancePrefs.uiSounds(context)) return
        val p = pool ?: return
        if (soundId <= 0 || !ready) return
        runCatching { p.play(soundId, 0.55f, 0.55f, 1, 0, 1f) }
    }

    fun release() {
        pool?.release()
        pool = null
        sendId = 0
        toggleId = 0
        deleteId = 0
        ready = false
    }

    /** 合成单声道 16-bit 44.1kHz WAV：快起音 + 幂律衰减，避免突兀"嘀"声。 */
    private fun writeWav(file: File, freq: Float, ms: Int): File {
        if (file.exists()) return file
        val sampleRate = 44_100
        val samples = (sampleRate * ms / 1000).coerceAtLeast(1)
        val pcm = ShortArray(samples)
        val amp = 0.32f
        for (i in 0 until samples) {
            val t = i.toFloat() / sampleRate
            val fade = (1f - i.toFloat() / samples).pow(1.6f)
            val v = (sin(2.0 * PI * freq * t) * amp * fade).toFloat()
            pcm[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        val bytes = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size * 2)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size * 2)
            pcm.forEach { putShort(it) }
        }.array()
        file.writeBytes(bytes)
        return file
    }
}
