package com.emberinn.engine.slash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 快捷回复槽 v2（对齐官方 quick-reply qrList 单条，含 executeOn* 9 迁移字段 + id）。 */
@Serializable
data class QuickReplyV2Slot(
    val id: Int,
    val label: String = "",
    val title: String = "",
    val message: String = "",
    val isHidden: Boolean = false,
    val executeOnStartup: Boolean = false,
    val executeOnUser: Boolean = false,
    val executeOnAi: Boolean = false,
    val preventAutoExecute: Boolean = false,
    val automationId: String = "",
    val placeBeforeInput: Boolean = false,
    val injectInput: Boolean = false,
    val disableSend: Boolean = false,
)

/** Quick Reply v2 单 set（一个预设 = 官方一个 set 文件）。 */
@Serializable
data class QuickReplyV2Set(
    val version: Int = 2,
    val name: String,
    @SerialName("qrList") val qrList: List<QuickReplyV2Slot> = emptyList(),
    val disableSend: Boolean = false,
    val placeBeforeInput: Boolean = false,
    val injectInput: Boolean = false,
)

/** 官方 settings.config.setList 显示条目（一个名字 + 是否显示）。 */
@Serializable
data class QuickReplyVisibleSet(
    val set: String,
    val isVisible: Boolean = true,
)

/** 官方 v2 settings 结构：{isEnabled, isCombined, config:{setList:[{set,isVisible}]}}。 */
@Serializable
data class QuickReplyV2Settings(
    val isEnabled: Boolean = false,
    val isCombined: Boolean = false,
    val config: QuickReplyV2Config = QuickReplyV2Config(),
) {
    val defaultConfig get() = config // alias for readability
}

@Serializable
data class QuickReplyV2Config(
    val setList: List<QuickReplyVisibleSet> = listOf(QuickReplyVisibleSet("Default", true)),
)

/** 向后兼容：官方 v1 QuickReplySlot + QuickReplyPreset（仍然可读老文件，显示/迁移用）。 */
@Serializable
data class QuickReplySlot(
    val mes: String,
    val label: String,
    val enabled: Boolean = true,
    @SerialName("automationId") val automationId: String = "",
    @SerialName("preventAutoExecute") val preventAutoExecute: Boolean = false,
)

@Serializable
data class QuickReplyPreset(
    val name: String,
    @SerialName("quickReplySlots") val slots: List<QuickReplySlot> = emptyList(),
)

/**
 * 官方 QuickReply 纯函数工具（全部 1:1 差分，脚本 quickreply-official.mjs → fixture 16 例）。
 * - migrateSetV1ToV2：官方 L69-L100，version!=2 时执行 9 字段映射 + id=idx+1 + 删 quickReplySlots
 * - visibleSetNames：settings.config.setList.filter{it.isVisible}.map{it.set}
 * - shouldAutoExecute：官方 AutoExecuteHandler 执行前过滤（isEnabled + setVisibility + isHidden +
 *   preventAutoExecute + phase 分支）
 */
object QuickReply {

    /** 官方 migrateSet（quick-reply/index.js L69-L100）：对输入 JsonObject IN-PLACE 改写并返回。 */
    fun migrateSetV1ToV2(input: JsonObject): JsonObject {
        val version = input["version"]?.jsonPrimitive?.intOrNull ?: 0
        if (version == 2) return input
        val out = input.toMutableMap()
        out["version"] = JsonPrimitive(2)
        out["disableSend"] = JsonPrimitive(out["quickActionEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
        out["placeBeforeInput"] = JsonPrimitive(out["placeBeforeInputEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
        out["injectInput"] = JsonPrimitive(out["AutoInputInject"]?.jsonPrimitive?.booleanOrNull ?: false)
        val slots = (out["quickReplySlots"] as? JsonArray) ?: buildJsonArray { }
        val qrList = buildJsonArray {
            slots.forEachIndexed { idx, slotEl ->
                val slot = slotEl.jsonObject
                val map = slot.toMutableMap()
                val id = JsonPrimitive(idx + 1)
                val label = JsonPrimitive(map["label"]?.jsonPrimitive?.contentOrNull ?: "")
                val title = JsonPrimitive(map["title"]?.jsonPrimitive?.contentOrNull ?: "")
                val message = JsonPrimitive(map["mes"]?.jsonPrimitive?.contentOrNull ?: "")
                val isHidden = JsonPrimitive(map["hidden"]?.jsonPrimitive?.booleanOrNull ?: false)
                val executeOnStartup = JsonPrimitive(map["autoExecute_appStartup"]?.jsonPrimitive?.booleanOrNull ?: false)
                val executeOnUser = JsonPrimitive(map["autoExecute_userMessage"]?.jsonPrimitive?.booleanOrNull ?: false)
                val executeOnAi = JsonPrimitive(map["autoExecute_botMessage"]?.jsonPrimitive?.booleanOrNull ?: false)
                val preventAutoExecute = JsonPrimitive(map["preventAutoExecute"]?.jsonPrimitive?.booleanOrNull ?: false)
                val automationId = JsonPrimitive(map["automationId"]?.jsonPrimitive?.contentOrNull ?: "")
                val placeBeforeInput = JsonPrimitive(map["placeBeforeInputEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                val injectInput = JsonPrimitive(map["AutoInputInject"]?.jsonPrimitive?.booleanOrNull ?: false)
                val disableSend = JsonPrimitive(map["quickActionEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                add(buildJsonObject {
                    put("id", id); put("label", label); put("title", title); put("message", message)
                    put("isHidden", isHidden); put("executeOnStartup", executeOnStartup)
                    put("executeOnUser", executeOnUser); put("executeOnAi", executeOnAi)
                    put("preventAutoExecute", preventAutoExecute); put("automationId", automationId)
                    put("placeBeforeInput", placeBeforeInput); put("injectInput", injectInput)
                    put("disableSend", disableSend)
                })
            }
        }
        out["qrList"] = qrList
        out.remove("quickReplySlots")
        return JsonObject(out)
    }

    /** 官方 visibleSetNames。 */
    fun visibleSetNames(settings: QuickReplyV2Settings): List<String> =
        settings.config.setList.filter { it.isVisible }.map { it.set }

    /**
     * 官方 AutoExecuteHandler 单 slot 执行判定。phase ∈ {startup, user, ai}。
     * setName=null 时跳过 set-visibility 过滤（对应 combined 场景或未绑定 set）。
     */
    fun shouldAutoExecute(
        slot: QuickReplyV2Slot?,
        phase: String,
        settings: QuickReplyV2Settings,
        setName: String? = null,
    ): Boolean {
        val vis = visibleSetNames(settings).toSet()
        if (!settings.isEnabled) return false
        if (!setName.isNullOrEmpty() && !settings.isCombined && setName !in vis) return false
        if (slot == null) return false
        if (slot.isHidden) return false
        if (slot.preventAutoExecute) return false
        return when (phase) {
            "startup" -> slot.executeOnStartup
            "user" -> slot.executeOnUser
            "ai" -> slot.executeOnAi
            else -> false
        }
    }
}

object QuickReplyExecutor {
    fun execute(preset: QuickReplyPreset, label: String, state: SlashState = SlashState()): String {
        val slot = preset.slots.firstOrNull { it.label == label && it.enabled } ?: return ""
        return SlashEngine.execute(slot.mes, state)
    }
}
