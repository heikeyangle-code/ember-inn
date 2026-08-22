package com.emberinn.app.renderer

import com.emberinn.app.ui.chat.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * P4 扩展桥安装器：把 st-api-shim.js 的桥请求接到 VM 差分锁定资产。
 *
 * 方法面（与 assets/kernel/js/st-api-shim.js 对齐）：
 *  - ctx.snapshot   → getContext().chat 快照（消息子集字段）+ name1/name2/characterId/chatId
 *  - metadata.get   → chatStore.metadata（官方 chat_metadata）
 *  - metadata.set   → chatStore.saveMetadata 即时落盘 + displayRevision 触发重渲
 *  - slash.run      → AppSlashExecutor.executeAsync（引擎 SlashEngine 1:1，返回管道输出）
 *  - macro.substitute → 引擎 MacroEngine.substitute（全量宏，差分锁定）
 */
object StApiShimInstaller {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun install(pool: KernelWebViewPool, vm: ChatViewModel) {
        pool.shimHandler = { method, paramsJson, respond ->
            scope.launch {
                try {
                    respond(when (method) {
                        "ctx.snapshot" -> vm.shimContextSnapshot()
                        "metadata.get" -> vm.shimChatMetadata().toString()
                        "metadata.set" -> {
                            val meta = json.parseToJsonElement(paramsJson).jsonObject["metadata"]
                                ?.jsonObject ?: JsonObject(emptyMap())
                            vm.shimSaveChatMetadata(meta)
                            """{"ok":true}"""
                        }
                        "slash.run" -> {
                            val line = json.parseToJsonElement(paramsJson).jsonObject["line"]
                                ?.jsonPrimitive?.contentOrNull ?: ""
                            """{"ok":true,"value":${jsonEncode(vm.shimSlash(line))}}"""
                        }
                        "macro.substitute" -> {
                            val text = json.parseToJsonElement(paramsJson).jsonObject["text"]
                                ?.jsonPrimitive?.contentOrNull ?: ""
                            """{"ok":true,"value":${jsonEncode(vm.shimSubstitute(text))}}"""
                        }
                        else -> """{"ok":false,"error":"unsupported method: $method"}"""
                    })
                } catch (e: Throwable) {
                    respond("""{"ok":false,"error":${jsonEncode(e.message ?: e.toString())}}""")
                }
            }
        }
    }

    fun uninstall(pool: KernelWebViewPool) { pool.shimHandler = null }

    /** 字符串→JSON 字符串字面量（kotlinx 无公开 escape API，借 JsonPrimitive 序列化） */
    private fun jsonEncode(s: String): String =
        json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(s))
}
