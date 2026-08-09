package com.emberinn.engine.slash

import com.emberinn.engine.worldinfo.WorldInfoEntry

/**
 * 世界书 → 快捷回复自动执行（对齐官方 extensions/quick-reply AutoExecuteHandler.handleWIActivation）：
 * 命中条目的 automationId 与快捷回复 automationId 匹配时，自动执行该快捷回复。
 */
object WorldInfoAutoExecute {

    /** 对齐 handleWIActivation：收集激活条目 automationId，从预设槽里筛出匹配项（保持配置顺序）。 */
    fun resolve(
        activatedEntries: List<WorldInfoEntry>,
        presets: List<QuickReplyPreset>,
    ): List<QuickReplySlot> {
        val automationIds = activatedEntries.mapNotNull { it.automationId }.filter { it.isNotEmpty() }
        if (automationIds.isEmpty()) return emptyList()
        return presets.flatMap { it.slots }.filter { it.automationId in automationIds }
    }
}

/** 自动执行器：维护 preventAutoExecute 栈（对齐官方 performAutoExecute）。 */
class AutoExecuteHandler {

    private val preventStack = ArrayDeque<Boolean>()

    /** 对齐官方 checkExecute：外层 prevent 时禁止自动执行。 */
    fun checkExecute(): Boolean = preventStack.lastOrNull() != true

    fun performAutoExecute(
        slots: List<QuickReplySlot>,
        presets: List<QuickReplyPreset>,
        state: SlashState = SlashState(),
    ) {
        for (slot in slots) {
            if (!checkExecute()) return
            withPrevent(slot) {
                presets.firstOrNull { preset -> preset.slots.any { it.label == slot.label } }
                    ?.let { QuickReplyExecutor.execute(it, slot.label, state) }
            }
        }
    }

    /** 官方 performAutoExecute 的 push/execute/pop 语义：执行前压入 prevent 栈，供后续槽位判定。 */
    fun <T> withPrevent(slot: QuickReplySlot, block: () -> T): T {
        preventStack.addLast(slot.preventAutoExecute)
        try {
            return block()
        } finally {
            preventStack.removeLast()
        }
    }
}
