package com.emberinn.app.ui.components

import androidx.compose.ui.graphics.Color

/** Color → #RRGGBB。 */
fun Color.toHex(): String = "#%02X%02X%02X".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/** 解析 #RRGGBB / #AARRGGBB（可带 #，允许 3 位简写）；失败返回 null。 */
fun parseHexColor(hex: String): Color? = runCatching {
    var h = hex.trim().removePrefix("#")
    if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
    val argb = when (h.length) {
        6 -> "FF$h"
        8 -> h
        else -> return null
    }
    Color(argb.toLong(16))
}.getOrNull()
