package com.emberinn.app.ui.chat

import com.mikepenz.markdown.model.State
import java.util.LinkedHashMap

/**
 * Markdown 解析结果 LRU 缓存。
 *
 * mikepenz 的 `Markdown(content=...)` 解析状态只活在行组合里：LazyColumn 滚出屏幕再滚回来，
 * 每行都要重新异步解析，解析期间行高为 0，表现为“一出现新消息就顿一下/闪空”。
 * 这里按内容缓存不可变的 `State.Success`（mikepenz 0.43 公开 API `parseMarkdown` 产物），
 * 滚回来的行首帧直接渲染缓存结果，不再重解析。
 *
 * 上限 32 条，超限淘汰最久未用；编辑/切 swipe 会换内容键，自然失效。
 */
object MarkdownCache {
    private const val MAX_ENTRIES = 32

    private val cache = object : LinkedHashMap<String, State.Success>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, State.Success>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: String): State.Success? = cache[key]

    @Synchronized
    fun put(key: String, state: State.Success) {
        cache[key] = state
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}

/**
 * 最终着色 AnnotatedString 的 LRU 缓存（OfficialMarkdownNode 产物）。
 *
 * OfficialMarkdownNode 的 built/styled 之前在组合里同步计算且只活在行组合里：
 * LazyColumn 滚出屏幕再滚回来，每条长消息都要在主线程重算一次（buildMarkdownAnnotatedString +
 * applyOfficialMarkers），这就是“来回滑动、出现别的消息就卡一下”的来源。
 * 这里按 内容+颜色+样式 缓存最终 AnnotatedString，滚回来的行首帧直接渲染。
 */
object AnnotatedCache {
    private const val MAX_ENTRIES = 64

    private val cache = object : LinkedHashMap<String, androidx.compose.ui.text.AnnotatedString>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, androidx.compose.ui.text.AnnotatedString>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: String): androidx.compose.ui.text.AnnotatedString? = cache[key]

    @Synchronized
    fun put(key: String, value: androidx.compose.ui.text.AnnotatedString) {
        cache[key] = value
    }
}
