package com.emberinn.app.data

/** 显示管线缓存版本：任何影响 displayTextOf 输出的设置（encode_tags/正则/允许列表）变更时 bump，
 *  ChatViewModel.displayCache 检测到版本变化即整体失效，设置改动即时生效。 */
object DisplayCacheVersion {
    @Volatile
    var version: Int = 0

    fun bump() {
        version++
    }
}
