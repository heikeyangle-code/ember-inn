package com.emberinn.app.renderer

import com.emberinn.app.ui.settings.TavernHelperPrefs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 酒馆助手（TH 兼容层）桥——自包含模块：TH 的全部宿主面逻辑只活在这个文件，
 * 不向 ChatStore/ChatViewModel 添加任何 TH 专属成员（架构约束：可扩展、零耦合）。
 *
 * 宿主能力经 [Host] 接口注入（组合根 = ChatScreen 的 DisposableEffect）：
 * ChatStore 只暴露通用原语 mutateMessages，消息结构操作在此组合实现。
 *
 * 方法面（assets/kernel/js/tavern-helper.js 对齐）：
 *  - th.config.get      → TavernHelperPrefs 配置 JSON
 *  - th.chat.get        → 当前聊天全量消息数组（ctx 快照复用）
 *  - th.message.set     → Host.editMessage
 *  - th.message.delete  → Host.deleteMessage
 *  - th.message.create  → Host.mutateMessages（官方元素直插；chat_message.ts L390+）
 *  - th.message.rotate  → Host.mutateMessages（[middle,end) 移到 begin；L468-488 splice 同构）
 */
object TavernHelperBridge {

    /** 宿主能力面：由聊天页组合根提供（VM 已有公共能力的最小投影）。 */
    interface Host {
        fun editMessage(index: Int, text: String)
        fun deleteMessage(index: Int)
        fun chatMetadataJson(): JsonObject
        fun contextSnapshotJson(): String
        fun isStreaming(): Boolean
        fun mutateMessages(transform: (List<JsonElement>) -> List<JsonElement>)
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** 返回响应信封 JSON；null = 非 TH 方法（交回调用方兜底） */
    fun handle(host: Host, method: String, paramsJson: String): String? = when (method) {
        "th.config.get" -> """{"ok":true,"value":${TavernHelperPrefs.current.toJsonString()}}"""
        "th.chat.get" -> {
            val snapshot = json.parseToJsonElement(host.contextSnapshotJson()).jsonObject
            """{"ok":true,"value":${snapshot["chat"]}}"""
        }
        "th.message.set" -> {
            val p = params(paramsJson) ?: return errMsg("th.message.set 参数无效")
            val id = p.int("message_id") ?: return errMsg("th.message.set 需要 message_id")
            host.editMessage(id, p.str("message").orEmpty())
            """{"ok":true}"""
        }
        "th.message.delete" -> {
            val p = params(paramsJson) ?: return errMsg("th.message.delete 参数无效")
            val id = p.int("message_id") ?: return errMsg("th.message.delete 需要 message_id")
            host.deleteMessage(id)
            """{"ok":true}"""
        }
        "th.message.create" -> {
            if (host.isStreaming()) return errMsg("生成中禁止结构变更")
            val p = params(paramsJson) ?: return errMsg("th.message.create 参数无效")
            val elements = p["elements"]?.jsonArray?.toList() ?: return errMsg("th.message.create 需要 elements")
            // 官方 createChatMessages：position 缺省 after_last；before_first=插队首；
            // {message_id:n}=插到该层之前（appendInPlace 语义）
            val at: Int? = when (val raw = p["position"]) {
                null -> null
                is JsonObject -> raw["message_id"]?.jsonPrimitive?.intOrNull
                is JsonPrimitive -> when (raw.contentOrNull) {
                    "after_last", null -> null
                    "before_first" -> 0
                    else -> return errMsg("position 仅支持 after_last/before_first/{message_id}")
                }
                else -> return errMsg("position 仅支持 after_last/before_first/{message_id}")
            }
            host.mutateMessages { list ->
                var insertAt = at
                if (insertAt != null && insertAt < 0) insertAt = list.size + insertAt
                val out = list.toMutableList()
                val idx = if (insertAt != null && insertAt in 0..out.size) insertAt else out.size
                out.addAll(idx, elements)
                out
            }
            """{"ok":true}"""
        }
        "th.message.rotate" -> {
            if (host.isStreaming()) return errMsg("生成中禁止结构变更")
            val p = params(paramsJson) ?: return errMsg("th.message.rotate 参数无效")
            val begin = p.int("begin") ?: return errMsg("需要 begin")
            val middle = p.int("middle") ?: return errMsg("需要 middle")
            val end = p.int("end") ?: return errMsg("需要 end")
            host.mutateMessages { list ->
                val b = begin.coerceIn(0, list.size)
                val m = middle.coerceIn(b, list.size)
                val e = end.coerceIn(m, list.size)
                val right = list.subList(m, e).toList()
                val out = list.toMutableList()
                out.subList(m, e).clear()
                out.addAll(b, right)
                out
            }
            """{"ok":true}"""
        }
        else -> null
    }

    private fun params(paramsJson: String): JsonObject? =
        runCatching { json.parseToJsonElement(paramsJson).jsonObject }.getOrNull()

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun errMsg(text: String): String =
        """{"ok":false,"error":${json.encodeToString(
            kotlinx.serialization.json.JsonPrimitive.serializer(),
            kotlinx.serialization.json.JsonPrimitive(text),
        )}}"""
}
