package com.emberinn.app.ui.design.components

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

/**
 * 回复音效（官方 power_user.play_message_sound 的 App 落地）：
 * 无需音频资产，用系统 ToneGenerator 短促确认音。
 * 登记边界：play_sound_unfocused（仅后台提示音）暂以前台同音实现，后台推送通知属未来项。
 */
object EmberSound {

    /** AI 回复完成：双短哔。ToneGenerator 用完即释放，避免长期占用音频资源。 */
    fun message(context: Context) {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
            android.os.Handler(context.mainLooper).postDelayed({ tone.release() }, 250)
        }
    }
}
