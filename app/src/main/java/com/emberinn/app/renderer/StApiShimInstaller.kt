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
 *  - metadata.get   → chatStore.metadata（官方 chat_metadata），信封 {ok,value}
 *  - metadata.set   → chatStore.saveMetadata 即时落盘 + displayRevision 触发重渲
 *  - slash.run      → AppSlashExecutor.executeAsync（引擎 SlashEngine 1:1，返回管道输出）
 *  - macro.substitute → 引擎 MacroEngine.substitute（全量宏，差分锁定）
 *  - host.clipboard → 系统剪贴板读（AppBridge.readClipboard；UI 层注入 reader）
 *
 * 酒馆助手变量族双作用域：
 *  - chat  = chat_metadata.variables → shim 组合 metadata.get/set（官方 variables.js 同源）
 *  - global = extension_settings.variables.global → variables.get/set 桥 + GlobalVariableStore
 *    （酒馆助手 variables.ts L81/L169 官方语义；MVU 卡硬依赖）
 */
object StApiShimInstaller {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun install(
        pool: KernelWebViewPool,
        vm: ChatViewModel,
        /** 宿主读剪贴板（host.clipboard）：由 UI 层提供（API29+ 需应用聚焦，空串=不可得） */
        clipboardReader: (() -> String)? = null,
        /** 变量族 global 作用域存储；null 时 shim 端 global 调用报错（能力未接） */
        globalVariables: GlobalVariableStore? = null,
        /** 酒馆助手宿主能力面（th.* 方法）；组合根组装，Bridge 模块自包含 */
        tavernHelperHost: TavernHelperBridge.Host? = null,
    ) {
        pool.shimHandler = { method, paramsJson, respond ->
            scope.launch {
                try {
                    respond(when (method) {
                        "ctx.snapshot" -> vm.shimContextSnapshot()
                        // 信封 {ok,value}：shim 端按 r.ok/r.value 解包（此前裸回 metadata 导致 getChatMetadata 恒空）
                        "metadata.get" -> """{"ok":true,"value":${vm.shimChatMetadata()}}"""
                        "metadata.set" -> {
                            val meta = json.parseToJsonElement(paramsJson).jsonObject["metadata"]
                                ?.jsonObject ?: JsonObject(emptyMap())
                            vm.shimSaveChatMetadata(meta)
                            """{"ok":true}"""
                        }
                        "variables.get" -> """{"ok":true,"value":${globalVariables?.read() ?: JsonObject(emptyMap())}}"""
                        "variables.set" -> {
                            val vars = json.parseToJsonElement(paramsJson).jsonObject["variables"]
                                ?.jsonObject ?: JsonObject(emptyMap())
                            globalVariables?.write(vars)
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
                        "host.clipboard" -> """{"ok":true,"value":${jsonEncode(clipboardReader?.invoke() ?: "")}}"""
                        // 酒馆助手兼容层（th.*）：独立模块经 Host 接口注入；非 TH 方法回 null 走兜底
                        else -> tavernHelperHost?.let { TavernHelperBridge.handle(it, method, paramsJson) }
                            ?: """{"ok":false,"error":"unsupported method: $method"}"""
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
