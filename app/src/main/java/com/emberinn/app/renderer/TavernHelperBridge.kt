package com.emberinn.app.renderer

import com.emberinn.app.ui.chat.ChatViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 酒馆助手（TH 兼容层）桥方法面——与 st-api-shim 的官方方法面分离：
 * StApiShimInstaller 的 else 分支委托到这里，返回 null 表示非 TH 方法。
 *
 * 方法面（assets/kernel/js/tavern-helper.js 对齐）：
 *  - th.config.get    → TavernHelperPrefs.current 配置 JSON（内核页启动拉取）
 *  - th.chat.get      → 当前聊天全量消息数组（官方 JSONL 元素形状，TH data 直引）
 *  - th.message.set   → VM.editMessage（正文改写；message_id=楼层下标）
 *  - th.message.delete→ VM.deleteMessage
 *
 * Phase 2 登记：th.message.create / th.message.rotate / 世界书族 / generate 族。
 */
object TavernHelperBridge {

    private val json = Json { ignoreUnknownKeys = true }

    /** 返回响应信封 JSON；null = 非 TH 方法（交回调用方兜底） */
    fun handle(vm: ChatViewModel, method: String, paramsJson: String): String? = when (method) {
        "th.config.get" -> """{"ok":true,"value":${TavernHelperPrefs.current.toJsonString()}}"""
        "th.chat.get" -> {
            // 复用 ctx 快照里的 chat 数组（同一次落盘真值，无额外状态）
            val snapshot = json.parseToJsonElement(vm.shimContextSnapshot()).jsonObject
            val chat = snapshot["chat"]
            """{"ok":true,"value":$chat}"""
        }
        "th.message.set" -> {
            val params = runCatching { json.parseToJsonElement(paramsJson).jsonObject }.getOrNull()
            val id = params?.get("message_id")?.jsonPrimitive?.intOrNull
                ?: return errMsg("th.message.set 需要 message_id")
            val message = params["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            vm.editMessage(id, message)
            """{"ok":true}"""
        }
        "th.message.delete" -> {
            val params = runCatching { json.parseToJsonElement(paramsJson).jsonObject }.getOrNull()
            val id = params?.get("message_id")?.jsonPrimitive?.intOrNull
                ?: return errMsg("th.message.delete 需要 message_id")
            vm.deleteMessage(id)
            """{"ok":true}"""
        }
        else -> null
    }

    private fun errMsg(text: String): String =
        """{"ok":false,"error":${json.encodeToString(
            kotlinx.serialization.json.JsonPrimitive.serializer(),
            kotlinx.serialization.json.JsonPrimitive(text),
        )}}"""
}
