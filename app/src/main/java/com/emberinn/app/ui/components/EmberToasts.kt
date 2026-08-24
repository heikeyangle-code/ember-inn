package com.emberinn.app.ui.components

import android.content.Context
import android.widget.Toast
import com.emberinn.app.data.OfficialThemeManager

/**
 * 官方 toastr 对应物：弹出位置由主题字段 toastr_position 驱动（六位置枚举）。
 * 平台边界：Android 11+ 系统对文本 Toast 忽略 setGravity——低版本完全生效，
 * 高版本回落系统默认位置（登记 HANDOFF §6.4；官方 toastr 是网页内浮层，
 * 如需高版本完全对齐需换应用内浮层实现）。
 */
object EmberToasts {
    fun show(context: Context, message: String, long: Boolean = false) {
        val toast = Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
        runCatching {
            toast.setGravity(OfficialThemeManager.shared(context).shellSettings().toastrGravity, 0, 0)
        }
        toast.show()
    }
}
