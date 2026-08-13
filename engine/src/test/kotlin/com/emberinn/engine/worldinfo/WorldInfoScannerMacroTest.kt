package com.emberinn.engine.worldinfo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方 checkWorldInfo 的 substituteParams 时机：关键词与条目内容在扫描时替换一次
 * （entry.content = substituteParams(entry.content)），替换后文本进入递归缓冲/预算/最终输出。
 * 差分 harness 的 substituteParams 是恒等打桩，这里用非恒等替换锁官方语义。
 */
class WorldInfoScannerMacroTest {

    private val substitute = MacroSubstituter { text ->
        text.replace("{{user}}", "小明")
    }

    @Test
    fun `keys are macro-substituted before matching and content is substituted once`() {
        val result = scan(
            chat = listOf("小明"),
            entries = listOf(entry(1, keys = listOf("{{user}}"), content = "你好{{user}}")),
        )
        assertEquals(listOf(1), result.activated.map { it.uid })
        // 激活内容已替换（官方 BUILDING PROMPT 直接消费 entry.content）
        assertEquals("你好小明", result.worldInfoBefore)
    }

    @Test
    fun `recursion buffer uses substituted content`() {
        val result = scan(
            chat = listOf("线索"),
            entries = listOf(
                entry(1, keys = listOf("线索"), content = "暗号{{user}}"),
                entry(2, keys = listOf("小明"), content = "宝藏"),
            ),
            recursive = true,
        )
        // 递归缓冲里是“暗号小明”，第二层 key“小明”因此能命中
        assertEquals(listOf(1, 2), result.activated.map { it.uid })
        // 同 order 时后激活条目 prepend（官方 unshift），故“宝藏”在前
        assertEquals("宝藏\n暗号小明", result.worldInfoBefore)
    }

    private fun scan(
        chat: List<String>,
        entries: List<WorldInfoEntry>,
        recursive: Boolean = false,
    ): WorldInfoResult = WorldInfoScanner(
        tokenCounter = TokenCounter { it.length },
        random = RandomProvider { 99.0 },
        substitute = substitute,
    ).scan(
        chat = chat,
        maxContext = 100,
        entries = entries,
        settings = WorldInfoSettings(depth = 2, budgetPercent = 100, recursive = recursive),
    )

    private fun entry(uid: Int, keys: List<String> = emptyList(), content: String): WorldInfoEntry =
        WorldInfoEntry(
            world = "w",
            uid = uid,
            order = 1,
            keys = keys,
            content = content,
            position = WorldInfoConstants.POSITION_BEFORE,
        )
}
