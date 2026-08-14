package com.emberinn.engine.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 官方 util.js mergeObjectWithYaml / excludeKeysByYaml 的 App 移植。
 * 支持：顶层/嵌套映射（缩进递归）、标量（引号/数字/布尔/null）、列表（- 项 / 内联 [a, b]）。
 * 多文档（---）、锚点/别名等复杂 YAML 登记边界（官方用完整 yaml 库）。
 */
object YamlMerge {

    /** 官方 mergeObjectWithYaml：顶层对象 → Object.assign 进目标；顶层数组 → 逐项 Object.assign。 */
    fun merge(obj: JsonObject, yaml: String): JsonObject {
        val parsed = parse(yaml) ?: return obj
        val out = obj.toMutableMap()
        val entries = if (parsed is JsonArray) {
            parsed.mapNotNull { it as? JsonObject }.flatMap { it.entries }
        } else {
            (parsed as? JsonObject)?.entries ?: return obj
        }
        entries.forEach { (k, v) -> out[k] = v }
        return JsonObject(out)
    }

    /** 官方 excludeKeysByYaml：数组 → 删每个元素；对象 → 删每个顶层键；字符串 → 删该键。 */
    fun excludeKeys(obj: JsonObject, yaml: String): JsonObject {
        val parsed = parse(yaml) ?: return obj
        val keys = when (parsed) {
            is JsonArray -> parsed.mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe() }
            is JsonObject -> parsed.keys
            is JsonPrimitive -> listOfNotNull(parsed.contentOrNullSafe())
            else -> emptyList()
        }
        val out = obj.toMutableMap()
        keys.forEach { out.remove(it) }
        return JsonObject(out)
    }

    /** 顶层标量映射 → 请求头。 */
    fun headers(yaml: String): Map<String, String> {
        val parsed = parse(yaml) as? JsonObject ?: return emptyMap()
        return parsed.mapNotNull { (k, v) ->
            val s = (v as? JsonPrimitive)?.contentOrNullSafe() ?: return@mapNotNull null
            k to s
        }.toMap()
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (this == JsonNull) null else content

    // ---------- 缩进递归 YAML 子集 ----------

    fun parse(yaml: String): JsonElement? {
        val lines = yaml.lines()
            .mapIndexedNotNull { i, l ->
                if (l.isBlank() || l.trimStart().startsWith("#")) null else i to l
            }
        if (lines.isEmpty()) return null
        var pos = 0

        fun indentOf(raw: String): Int = raw.indexOfFirst { it != ' ' }.let { if (it < 0) 0 else it }

        fun parseScalar(raw: String): JsonElement {
            val v = unquote(raw)
            return when {
                v == "null" || v.isEmpty() -> JsonNull
                v == "true" -> JsonPrimitive(true)
                v == "false" -> JsonPrimitive(false)
                v.toIntOrNull() != null -> JsonPrimitive(v.toInt())
                v.toDoubleOrNull() != null -> JsonPrimitive(v.toDouble())
                v.startsWith("[") && v.endsWith("]") -> parseInlineList(v) ?: JsonPrimitive(v)
                else -> JsonPrimitive(v)
            }
        }

        fun splitKV(line: String): Pair<String, String>? {
            val idx = line.indexOf(':')
            if (idx <= 0) return null
            val key = line.substring(0, idx).trim().trim('"', '\'', ' ')
            if (key.isEmpty()) return null
            return key to line.substring(idx + 1).trim()
        }

        /** 解析一个缩进块：isList=true 时按列表项解析，否则按 key: value 映射解析。 */
        fun parseBlock(indent: Int, isList: Boolean): JsonElement {
            val map = LinkedHashMap<String, JsonElement>()
            val list = mutableListOf<JsonElement>()
            while (pos < lines.size) {
                val (_, raw) = lines[pos]
                val cur = indentOf(raw)
                if (cur < indent) break
                if (cur > indent) {
                    pos++
                    continue
                }
                val line = raw.trim()
                if (line.startsWith("- ")) {
                    val item = line.removePrefix("- ").trim()
                    val kv = splitKV(item)
                    if (kv != null && kv.second.isEmpty()) {
                        // 列表项为嵌套块
                        val childIndent = lines.getOrNull(pos + 1)?.let { indentOf(it.second) } ?: -1
                        if (childIndent > cur) {
                            pos++
                            val child = parseBlock(childIndent, false)
                            list += buildJsonObject { put(kv.first, child) }
                            continue
                        }
                        list += parseScalar(kv.first)
                    } else if (kv != null) {
                        list += buildJsonObject { put(kv.first, parseScalar(kv.second)) }
                    } else {
                        list += parseScalar(item)
                    }
                    pos++
                } else {
                    val kv = splitKV(line)
                    if (kv == null) {
                        pos++
                        continue
                    }
                    val (key, value) = kv
                    if (value.isEmpty()) {
                        val childIndent = lines.getOrNull(pos + 1)?.let { indentOf(it.second) } ?: -1
                        if (childIndent > cur) {
                            pos++
                            map[key] = parseBlock(childIndent, lines[pos].second.trimStart().startsWith("- "))
                            continue
                        }
                        map[key] = JsonNull
                    } else {
                        map[key] = parseScalar(value)
                    }
                    pos++
                }
            }
            return if (isList) JsonArray(list) else buildJsonObject { map.forEach { (k, v) -> put(k, v) } }
        }

        val firstIndent = indentOf(lines[0].second)
        val firstIsList = lines[0].second.trimStart().startsWith("- ")
        return parseBlock(firstIndent, firstIsList)
    }

    private fun parseInlineList(v: String): JsonArray? = runCatching {
        val inner = v.substring(1, v.length - 1)
        if (inner.isBlank()) return JsonArray(emptyList())
        JsonArray(inner.split(",").map { it.trim() }.map { parseScalarForInline(it) })
    }.getOrNull()

    private fun parseScalarForInline(raw: String): JsonElement {
        val v = unquote(raw)
        return when {
            v == "null" || v.isEmpty() -> JsonNull
            v == "true" -> JsonPrimitive(true)
            v == "false" -> JsonPrimitive(false)
            v.toIntOrNull() != null -> JsonPrimitive(v.toInt())
            v.toDoubleOrNull() != null -> JsonPrimitive(v.toDouble())
            else -> JsonPrimitive(v)
        }
    }

    private fun unquote(raw: String): String {
        if (raw.length >= 2 && ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'")))) {
            return raw.substring(1, raw.length - 1)
        }
        return raw
    }
}
