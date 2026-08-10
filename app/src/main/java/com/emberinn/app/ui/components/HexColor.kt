package com.emberinn.app.ui.components

import androidx.compose.ui.graphics.Color

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
