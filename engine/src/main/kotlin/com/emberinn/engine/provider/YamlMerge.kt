package com.emberinn.engine.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.yaml.snakeyaml.Yaml
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * 官方 util.js mergeObjectWithYaml / excludeKeysByYaml 的 App 移植。
 *
 * 解析用 SnakeYAML（与官方 js-yaml `yaml.parse` 语义对齐）：
 * - 锚点/别名/合并键（<<）由 SnakeYAML 原生解析（已用 2.6 实测）；
 * - 多文档（---）抛 ComposerException → 与官方 try/catch 一样整体忽略、不合并；
 * - 时间戳 → ISO-8601 字符串（官方 yaml 库解析为 Date，JSON.stringify 时输出 ISO）。
 *
 * 合并/删除语义逐字对齐官方 util.js：顶层对象 → Object.assign；顶层数组 → 逐项 Object.assign；
 * exclude 数组 → 删每个键；对象 → 删每个顶层键；字符串 → 删该键。
 */
object YamlMerge {

    private val yaml = Yaml()

    /** 官方 mergeObjectWithYaml：顶层对象 → Object.assign 进目标；顶层数组 → 逐项 Object.assign。 */
    fun merge(obj: JsonObject, yamlString: String): JsonObject {
        val parsed = parse(yamlString) ?: return obj
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
    fun excludeKeys(obj: JsonObject, yamlString: String): JsonObject {
        val parsed = parse(yamlString) ?: return obj
        val keys = when (parsed) {
            is JsonArray -> parsed.map { it.toJsKeyString() }
            is JsonObject -> parsed.keys
            is JsonPrimitive -> listOfNotNull(parsed.toJsKeyString())
            else -> emptyList()
        }
        val out = obj.toMutableMap()
        keys.forEach { out.remove(it) }
        return JsonObject(out)
    }

    /** 顶层标量映射 → 请求头。 */
    fun headers(yamlString: String): Map<String, String> {
        val parsed = parse(yamlString) as? JsonObject ?: return emptyMap()
        return parsed.mapNotNull { (k, v) ->
            val s = (v as? JsonPrimitive)?.contentOrNullSafe() ?: return@mapNotNull null
            k to s
        }.toMap()
    }

    /** SnakeYAML 解析：失败（含多文档）返回 null，与官方 try/catch 静默一致。 */
    fun parse(yamlString: String): JsonElement? = try {
        toJsonElement(yaml.load<Any?>(yamlString))
    } catch (_: Throwable) {
        null
    }

    private fun toJsonElement(v: Any?): JsonElement? = when (v) {
        null -> JsonNull
        is Map<*, *> -> buildJsonObject {
            v.forEach { (k, value) -> toJsonElement(value)?.let { put(k.toString(), it) } }
        }
        is List<*> -> JsonArray(v.mapNotNull { toJsonElement(it) })
        is Boolean -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is Double -> JsonPrimitive(v)
        is Float -> JsonPrimitive(v.toDouble())
        is Byte -> JsonPrimitive(v.toInt())
        is Short -> JsonPrimitive(v.toInt())
        is Date -> JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(v.toInstant()))
        else -> JsonPrimitive(v.toString())
    }

    /** JS delete obj[key] 的键字符串化：对象在 JS 里是 "[object Object]"；JsonNull 也是 JsonPrimitive，先判空。 */
    private fun JsonElement.toJsKeyString(): String = when (this) {
        JsonNull -> "null"
        is JsonObject -> "[object Object]"
        is JsonArray -> "[object Object]"
        is JsonPrimitive -> contentOrNullSafe() ?: "null"
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (this == JsonNull) null else content
}
