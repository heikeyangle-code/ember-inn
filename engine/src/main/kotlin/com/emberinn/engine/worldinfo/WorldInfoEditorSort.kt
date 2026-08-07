package com.emberinn.engine.worldinfo

/**
 * 对齐官方 world-info.js sortWorldInfoEntries（编辑器排序，displayIndex 的消费方）：
 * - custom：displayIndex 升序（缺失用 uid 兜底），次级 order 降序，末级 uid 升序
 * - priority：disable 最末、constant 最前，其余同 custom
 * - default：按指定字段 + 方向；length 按字符串长度
 * - search：官方用 UI 搜索评分（App 层），引擎不实现，返回原顺序
 */
object WorldInfoEditorSort {

    fun sort(
        entries: List<WorldInfoEntry>,
        rule: String = "custom",
        order: String = "asc",
        field: String = "uid",
    ): List<WorldInfoEntry> {
        if (entries.size <= 1 || rule == "search") return entries
        val orderSign = if (order == "asc") 1 else -1

        val primary: Comparator<WorldInfoEntry> = when (rule) {
            "custom" -> compareBy { it.displayIndex ?: it.uid }
            "priority" -> compareBy { if (it.disable) 2 else if (it.constant) 0 else 1 }
            "length" -> {
                val base: Comparator<WorldInfoEntry> = compareBy { it.name.length }
                if (orderSign < 0) base.reversed() else base
            }
            else -> {
                val base: Comparator<WorldInfoEntry> = when (field) {
                    "order" -> compareBy { it.order }
                    "content" -> compareBy { it.content }
                    "name" -> compareBy { it.name }
                    else -> compareBy { it.uid }
                }
                if (orderSign < 0) base.reversed() else base
            }
        }

        // custom/priority 官方不随 asc/desc 反转；次级固定 order 降序，末级 uid 升序
        val comparator = when (rule) {
            "custom", "priority" -> primary
                .thenByDescending { it.order }
                .thenBy { it.uid }
            "length" -> primary
                .thenByDescending { it.order }
                .thenBy { it.uid }
            else -> primary
                .thenByDescending { it.order }
                .thenBy { it.uid }
        }
        return entries.sortedWith(comparator)
    }
}
