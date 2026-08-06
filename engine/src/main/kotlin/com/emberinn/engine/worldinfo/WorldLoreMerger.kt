package com.emberinn.engine.worldinfo

/** 对齐 getSortedEntries：四类书合并 + 插入策略 + order 降序（sortFn）。 */
object WorldLoreMerger {

    const val EVENLY = 0
    const val CHARACTER_FIRST = 1
    const val GLOBAL_FIRST = 2

    fun merge(
        global: List<WorldInfoEntry>,
        character: List<WorldInfoEntry>,
        chat: List<WorldInfoEntry>,
        persona: List<WorldInfoEntry>,
        strategy: Int = CHARACTER_FIRST,
    ): List<WorldInfoEntry> {
        val sort = { l: List<WorldInfoEntry> -> l.sortedWith(compareByDescending { it.order }) }
        val base = when (strategy) {
            CHARACTER_FIRST -> sort(character) + sort(global)
            GLOBAL_FIRST -> sort(global) + sort(character)
            else -> sort(global + character)
        }
        return sort(chat) + sort(persona) + base
    }
}
