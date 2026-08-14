package com.emberinn.engine.provider

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 官方 util.js mergeObjectWithYaml / excludeKeysByYaml 的 App 移植。
 * 官方用完整 yaml 库；此处支持官方常见用法：顶层 "key: value" 标量映射（引号/数字/布尔/null）、
 * exclude 的数组/对象/字符串三种形态。嵌套对象/列表/多文档等复杂 YAML 登记为边界（未支持）。
 */
object YamlMerge {

    /** 官方 mergeObjectWithYaml：yaml 顶层对象 → Object.assign 进目标；顶层数组 → 逐项 Object.assign。 */
    fun merge(obj: JsonObject, yaml: String): JsonObject {
        val parsed = parseTopLevel(yaml) ?: return obj
        val out = obj.toMutableMap()
        val entries = if (parsed is List<*>) {
            parsed.filterIsInstance<Map<String, JsonPrimitive>>().flatMap { it.entries }
        } else {
            (parsed as? Map<String, JsonPrimitive>)?.entries ?: return obj
        }
        entries.forEach { (k, v) -> out[k] = v }
        return JsonObject(out)
    }

    /** 官方 excludeKeysByYaml：数组 → 删每个元素；对象 → 删每个顶层键；字符串 → 删该键。 */
    fun excludeKeys(obj: JsonObject, yaml: String): JsonObject {
        val parsed = parseTopLevel(yaml) ?: return obj
        val keys = when (parsed) {
            is List<*> -> parsed.filterIsInstance<String>()
            is Map<*, *> -> parsed.keys.filterIsInstance<String>()
            is String -> listOf(parsed)
            else -> emptyList()
        }
        val out = obj.toMutableMap()
        keys.forEach { out.remove(it) }
        return JsonObject(out)
    }

    /** 顶层 "key: value" 行 → 标量 JsonPrimitive（官方 yaml 标量语义子集）。 */
    fun headers(yaml: String): Map<String, String> {
        val parsed = parseTopLevel(yaml) as? Map<String, JsonPrimitive> ?: return emptyMap()
        return parsed.mapNotNull { (k, v) ->
            val value = v.contentOrNullSafe() ?: return@mapNotNull null
            k to value
        }.toMap()
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (this == JsonNull) null else content

    private fun parseTopLevel(yaml: String): Any? {
        val lines = yaml.lines()
        val map = linkedMapOf<String, JsonPrimitive>()
        val list = mutableListOf<Any>()
        var isList = false
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            // 嵌套/列表等复杂结构：缩进行直接跳过（边界登记）
            if (raw.startsWith(" ") || raw.startsWith("\t")) continue
            if (line.startsWith("-")) {
                // 顶层数组：元素为 "key: value" 的项进 map 项；纯字符串进字符串列表
                isList = true
                val item = line.removePrefix("-").trim()
                val kv = splitKeyValue(item)
                if (kv != null) list += mapOf(kv.first to kv.second)
                else if (item.isNotEmpty()) list += unquote(item)
                continue
            }
            if (isList) continue
            val kv = splitKeyValue(line) ?: continue
            map[kv.first] = kv.second
        }
        if (isList) return list.ifEmpty { null }
        return map.ifEmpty { null }
    }

    private fun splitKeyValue(line: String): Pair<String, JsonPrimitive>? {
        val idx = line.indexOf(':')
        if (idx <= 0) return null
        val key = line.substring(0, idx).trim().trim('"', '\'', ' ')
        if (key.isEmpty()) return null
        val raw = line.substring(idx + 1).trim()
        val value = unquote(raw)
        val prim = when {
            value == "null" || value.isEmpty() -> JsonNull
            value == "true" -> JsonPrimitive(true)
            value == "false" -> JsonPrimitive(false)
            value.toIntOrNull() != null -> JsonPrimitive(value.toInt())
            value.toDoubleOrNull() != null -> JsonPrimitive(value.toDouble())
            else -> JsonPrimitive(value)
        }
        return key to prim
    }

    private fun unquote(raw: String): String {
        if (raw.length >= 2 && ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'")))) {
            return raw.substring(1, raw.length - 1)
        }
        return raw
    }
}
