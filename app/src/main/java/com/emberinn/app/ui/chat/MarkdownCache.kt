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
