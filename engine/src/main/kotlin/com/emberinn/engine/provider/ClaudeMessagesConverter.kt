package com.emberinn.engine.provider

import com.emberinn.engine.media.MediaConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 对齐官方 convertClaudeMessages（src/prompt-converters.js，逐字差分）：
 * 前置 system 提取（example 名字前缀）、assistant tool_calls→tool_use、tool→tool_result、
 * system→user、字符串/数组内容块转换（image_url→image、text 名字前缀+零宽空格）、
 * 助手图片搬移到下一条 user 消息、prefill 尾追加、同角色合并、useTools=false 时工具块转文本。
 */
object ClaudeMessagesConverter {

    data class Result(val messages: List<JsonObject>, val systemPrompt: List<JsonObject>)

    private val json = Json { ignoreUnknownKeys = true }

    fun convert(
        messages: List<JsonObject>,
        prefillString: String = "",
        useSysPrompt: Boolean = false,
        useTools: Boolean = false,
        names: PromptNames = PromptNames(),
        promptPlaceholder: String = "Let's get started.",
    ): Result {
        val msgs = messages.map { JsonObject(it) }.toMutableList()
        val systemPrompt = mutableListOf<JsonObject>()

        if (useSysPrompt) {
            var i = 0
            while (i < msgs.size && msgs[i].role() == "system") {
                val content = prefixExampleNames(msgs[i].str("content").orEmpty(), msgs[i].name(), names)
                msgs[i] = msgs[i].set("content", JsonPrimitive(content))
                systemPrompt += buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(content))
                }
                i++
            }
            if (i > 0) repeat(i) { msgs.removeAt(0) }
            if (msgs.isEmpty()) {
                msgs.add(0, buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive(promptPlaceholder))
                })
            }
        }

        val converted = mutableListOf<JsonObject>()
        for (raw in msgs) {
            var msg = raw

            // assistant + tool_calls → tool_use 内容块（覆盖 content）
            if (msg.role() == "assistant" && msg["tool_calls"] is JsonArray) {
                val toolUse = msg["tool_calls"]!!.jsonArray.map { tc ->
                    val t = tc.jsonObject
                    buildJsonObject {
                        put("type", JsonPrimitive("tool_use"))
                        put("id", JsonPrimitive(t.str("id").orEmpty()))
                        put("name", JsonPrimitive(t.function()?.str("name").orEmpty()))
                        // 官方 parse：字符串才 JSON.parse（非法抛错），对象/其它原样；缺失则字段省略
                        val argsEl = t.function()?.get("arguments")
                        if (argsEl != null) {
                            put("input", jsParseArgs(argsEl))
                        }
                    }
                }
                msg = msg.set("content", JsonArray(toolUse))
            }

            // tool → user + tool_result
            if (msg.role() == "tool") {
                val toolResult = buildJsonObject {
                    put("type", JsonPrimitive("tool_result"))
                    put("tool_use_id", JsonPrimitive(msg.str("tool_call_id").orEmpty()))
                    put("content", msg["content"] ?: JsonPrimitive(""))
                }
                msg = msg.set("role", JsonPrimitive("user")).set("content", JsonArray(listOf(toolResult)))
            }

            // 残留 system → user，并删除 name（避免后面再加前缀）
            if (msg.role() == "system") {
                val fixed = prefixExampleNames(msg.str("content").orEmpty(), msg.name(), names)
                msg = msg.set("content", JsonPrimitive(fixed)).set("role", JsonPrimitive("user")).without("name")
            }

            msg = when (val content = msg["content"]) {
                is JsonPrimitive -> {
                    val name = msg.name()
                    var text = content.content
                    if (!name.isNullOrEmpty()) text = "$name: $text"
                    msg.set("content", JsonArray(listOf(buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(text))
                    })))
                }
                is JsonArray -> {
                    val name = msg.name()
                    val mapped = content.map { c ->
                        val part = c.jsonObject
                        when (part.str("type")) {
                            "image_url" -> MediaConverter.convertClaudePart(part, null)
                            "text" -> MediaConverter.convertClaudePart(part, name)
                            else -> part
                        }
                    }
                    msg.set("content", JsonArray(mapped))
                }
                else -> msg
            }
            msg = msg.without("name", "tool_calls", "tool_call_id")
            converted += msg
        }

        // 助手消息中的图片搬移到下一条用户消息；没有则插入新的用户消息
        var i = 0
        while (i < converted.size) {
            val cur = converted[i]
            if (cur.role() == "assistant" && cur.contentParts().any { it.str("type") == "image" }) {
                var j = i + 1
                while (j < converted.size && converted[j].role() != "user") j++
                if (j >= converted.size) {
                    converted.add(i + 1, buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonArray(emptyList()))
                    })
                }
                val parts = cur.contentParts()
                val images = parts.filter { it.str("type") == "image" }
                val rest = parts.filter { it.str("type") != "image" }
                val target = converted[j]
                val targetParts = target.contentParts().toMutableList()
                targetParts.addAll(images)
                converted[j] = target.set("content", JsonArray(targetParts))
                converted[i] = cur.set("content", JsonArray(rest))
            }
            i++
        }

        if (prefillString.isNotEmpty()) {
            converted += buildJsonObject {
                put("role", JsonPrimitive("assistant"))
                put("content", JsonArray(listOf(buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(prefillString.trimEnd()))
                })))
            }
        }

        // 同角色相邻消息合并
        val merged = mutableListOf<JsonObject>()
        for (m in converted) {
            val last = merged.lastOrNull()
            if (last != null && last.role() == m.role()) {
                val parts = last.contentParts().toMutableList()
                parts.addAll(m.contentParts())
                merged[merged.lastIndex] = last.set("content", JsonArray(parts))
            } else {
                merged += m
            }
        }

        if (!useTools) {
            val noTools = merged.map { m ->
                val parts = m.contentParts().map { p ->
                    when (p.str("type")) {
                        "tool_use" -> buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive(jsStringify(p["input"] ?: JsonNull)))
                        }
                        "tool_result" -> buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", p["content"] ?: JsonPrimitive(""))
                        }
                        else -> p
                    }
                }
                m.set("content", JsonArray(parts))
            }
            return Result(noTools, systemPrompt)
        }

        return Result(merged, systemPrompt)
    }

    /**
     * 对齐官方 cachingAtDepthForClaude：从尾部回扫，跳过 assistant prefill，
     * 在 depth 与 depth+2 的角色切换处给最后内容块加 cache_control（直接改消息数组）。
     */
    fun atDepth(messages: MutableList<JsonObject>, cachingAtDepth: Int, ttl: String) {
        var passedThePrefill = false
        var depth = 0
        var previousRoleName = ""
        for (i in messages.indices.reversed()) {
            val msg = messages[i]
            if (!passedThePrefill && msg.role() == "assistant") continue
            passedThePrefill = true
            if (msg.role() != previousRoleName) {
                if (depth == cachingAtDepth || depth == cachingAtDepth + 2) {
                    val content = msg.contentParts().toMutableList()
                    if (content.isNotEmpty()) {
                        val last = content.last().toMutableMap()
                        last["cache_control"] = buildJsonObject {
                            put("type", JsonPrimitive("ephemeral"))
                            put("ttl", JsonPrimitive(ttl))
                        }
                        content[content.lastIndex] = JsonObject(last)
                        messages[i] = msg.set("content", JsonArray(content))
                    }
                }
                if (depth == cachingAtDepth + 2) break
                depth += 1
                previousRoleName = msg.role()
            }
        }
    }

    /** 前置 system 的 example 名字前缀（对齐官方，含 startsWithGroupName 检查）。 */
    private fun prefixExampleNames(content: String, name: String?, names: PromptNames): String {
        var c = content
        if (names.userName.isNotEmpty() && name == "example_user") {
            if (!c.startsWith("${names.userName}: ")) c = "${names.userName}: $c"
        }
        if (names.charName.isNotEmpty() && name == "example_assistant") {
            if (!c.startsWith("${names.charName}: ") && !names.startsWithGroupName(c)) c = "${names.charName}: $c"
        }
        return c
    }

    /** JS JSON.parse 语义：非法 JSON 抛错；数字按 JS Number 规范化（1.0 → 1）。 */
    private fun jsParse(text: String): JsonElement = normalizeJsNumbers(json.parseToJsonElement(text))

    /** 官方 parse(str) 语义：字符串才 JSON.parse，其它（对象/数字/布尔/null）原样。 */
    private fun jsParseArgs(arguments: JsonElement): JsonElement = when (arguments) {
        is JsonPrimitive -> if (arguments.isString) jsParse(arguments.content) else normalizeJsNumbers(arguments)
        else -> normalizeJsNumbers(arguments)
    }

    /** JS JSON.stringify 语义（用于 !useTools 时 tool_use → text）。 */
    private fun jsStringify(el: JsonElement): String = when (el) {
        is JsonNull -> "null"
        is JsonPrimitive -> if (el.isString) "\"${escapeJson(el.content)}\"" else (normalizeJsNumbers(el) as JsonPrimitive).content
        is JsonArray -> "[" + el.joinToString(",") { jsStringify(it) } + "]"
        is JsonObject -> "{" + el.entries.joinToString(",") { (k, v) -> "\"${escapeJson(k)}\":" + jsStringify(v) } + "}"
    }

    private fun escapeJson(s: String): String = buildString {
        for (ch in s) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
    }

    /** JS Number 规范化：整数值去小数点（1.0→1），指数改小写 e 并带符号。 */
    internal fun normalizeJsNumbers(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.mapValues { (_, v) -> normalizeJsNumbers(v) })
        is JsonArray -> JsonArray(el.map { normalizeJsNumbers(it) })
        is JsonPrimitive -> {
            if (el.isString || el.content == "true" || el.content == "false" || el.content == "null") el
            else {
                val d = el.content.toDoubleOrNull()
                if (d != null && d.isFinite()) jsNumber(d) else el
            }
        }
        JsonNull -> JsonNull
    }

    private fun jsNumber(d: Double): JsonPrimitive {
        // 注意：JsonPrimitive(String) 会按字符串处理，数字原始值必须用 JsonUnquotedLiteral
        if (d == Math.floor(d) && Math.abs(d) < 1e21) return JsonUnquotedLiteral(d.toLong().toString())
        var s = d.toString()
        if (s.endsWith(".0")) s = s.dropLast(2)
        val e = s.indexOf('E')
        if (e >= 0) {
            val exp = s.substring(e + 1).toInt()
            return JsonUnquotedLiteral(s.substring(0, e) + "e" + (if (exp >= 0) "+$exp" else exp.toString()))
        }
        return JsonUnquotedLiteral(s)
    }
}
