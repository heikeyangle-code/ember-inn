package com.emberinn.engine.slash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 快捷回复槽（对齐官方 quick-replies：mes=斜杠链 / label / enabled / automationId / preventAutoExecute）。 */
@Serializable
data class QuickReplySlot(
    val mes: String,
    val label: String,
    val enabled: Boolean = true,
    @SerialName("automationId")
    val automationId: String = "",
    @SerialName("preventAutoExecute")
    val preventAutoExecute: Boolean = false,
)

/** 快捷回复预设（对齐官方 quick-replies 文件）。 */
@Serializable
data class QuickReplyPreset(
    val name: String,
    @SerialName("quickReplySlots")
    val slots: List<QuickReplySlot> = emptyList(),
)

object QuickReplyExecutor {

    fun execute(
        preset: QuickReplyPreset,
        label: String,
        state: SlashState = SlashState(),
    ): String {
        val slot = preset.slots.firstOrNull { it.label == label && it.enabled }
            ?: return ""
        return SlashEngine.execute(slot.mes, state)
    }
}
