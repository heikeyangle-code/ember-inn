package com.emberinn.engine.slash

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * 官方 variables.js 数学/布尔/长度/排序 斜杠命令纯逻辑（SillyTavern 1.18.0 / 8172dcd）。
 * 逐字对照：scripts/diff/slash-math-official.mjs → SlashMathDiffTest。
 * 变量解析：官方查作用域变量（scope/local/global），App 只传本会话局部变量 map，查不到回退字面量。
 */
object SlashMathEngine {

    fun isTrueBoolean(arg: String?): Boolean =
        arg?.trim()?.lowercase() in setOf("on", "true", "1")

    fun isFalseBoolean(arg: String?): Boolean =
        arg?.trim()?.lowercase() in setOf("off", "false", "0")

    /** 官方 variables.js:494 evalBoolean（a/b 为 String 或 Double；null 表示未提供）。 */
    fun evalBoolean(rule: String?, a: Any?, b: Any?): Boolean {
        if (a == null) {
            throw IllegalArgumentException("Left operand is not provided")
        }
        if (b == null) {
            when (rule) {
                null, "not" -> {
                    val resultOnTruthy = rule != "not"
                    val s = a.toString()
                    if (isTrueBoolean(s)) return resultOnTruthy
                    if (isFalseBoolean(s)) return !resultOnTruthy
                    return if (jsTruthy(a)) resultOnTruthy else !resultOnTruthy
                }
                else -> throw IllegalArgumentException(
                    "Unknown boolean comparison rule for truthy check. If right operand is not provided, the rule must not provided or be 'not'. Provided: $rule",
                )
            }
        }
        val r = rule ?: "eq"
        if (a is Double && b is Double) {
            when (r) {
                "gt" -> return a > b
                "gte" -> return a >= b
                "lt" -> return a < b
                "lte" -> return a <= b
                "eq" -> return a == b
                "neq" -> return a != b
                "in", "nin" -> Unit // 官方 fall through 到字符串比较
                else -> throw IllegalArgumentException(
                    "Unknown boolean comparison rule for type number. Accepted: gt, gte, lt, lte, eq, neq. Provided: $r",
                )
            }
        }
        val aString = if (a is String) a.lowercase() else jsStringify(a).lowercase()
        val bString = if (b is String) b.lowercase() else jsStringify(b).lowercase()
        return when (r) {
            "in" -> aString.contains(bString)
            "nin" -> !aString.contains(bString)
            "eq" -> aString == bString
            "neq" -> aString != bString
            else -> throw IllegalArgumentException(
                "Unknown boolean comparison rule for type string. Accepted: in, nin, eq, neq. Provided: $r",
            )
        }
    }

    private fun jsTruthy(a: Any?): Boolean = when (a) {
        is String -> a.isNotEmpty()
        is Double -> a != 0.0
        is Boolean -> a
        else -> true
    }

    /** JS JSON.parse 语义：只接受对象/数组/带引号字符串/数字/布尔/null；
     *  kotlinx 宽松模式会把裸标识符（如 hello）解析成非字符串 Primitive，按 JS 语义视为无效。 */
    private fun parseJsonValue(value: String): JsonElement? {
        val el = runCatching { Json.parseToJsonElement(value) }.getOrNull() ?: return null
        return when (el) {
            is JsonObject, is JsonArray, JsonNull -> el
            is JsonPrimitive -> when {
                el.isString -> el
                el.content.toDoubleOrNull() != null -> el
                el.content in setOf("true", "false") -> el
                else -> null
            }
            else -> null
        }
    }

    /** 官方 JSON.stringify 对 number 的输出（整数值回 Long 字符串，避免 1.0E7 科学计数法差异）。 */
    private fun jsStringify(a: Any?): String = when (a) {
        is Double -> jsNumberToString(a)
        is Boolean -> a.toString()
        is String -> "\"$a\"" // 官方只对非 string 走 JSON.stringify，字符串分支不会进这里
        else -> a?.toString() ?: "null"
    }

    fun jsNumberToString(d: Double): String = when {
        d.isNaN() -> "NaN"
        d == Double.POSITIVE_INFINITY -> "Infinity"
        d == Double.NEGATIVE_INFINITY -> "-Infinity"
        abs(d) < 1e21 -> {
            // JS String(number) 在 1e21 前用定点表示；整数值回整数串（超 Long 范围用 BigDecimal）
            val whole = if (abs(d) < 9.2e18) d.toLong() else d.toBigDecimal().toBigInteger()
            if (d == whole.toDouble()) whole.toString() else d.toString()
        }
        else -> d.toString()
    }

    /** 官方 variables.js:620 parseNumericSeries。 */
    fun parseNumericSeries(value: String, scope: Map<String, String?> = emptyMap()): List<Double> {
        var values: List<String> = value.split(" ")
        if (values.size == 1) {
            if (values[0].startsWith("[")) {
                values = parseJsonArrayStrings(values[0])
            } else {
                values = values[0].split(" ")
            }
        }
        return values
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map {
                val direct = it.toDoubleOrNull()
                if (direct != null) direct
                else resolveVariable(it, scope)?.toDoubleOrNull() ?: Double.NaN
            }
            .filter { !it.isNaN() }
    }

    /** 官方 convertValueType(str, 'array') ≈ JSON.parse；元素转成“内容字符串”，数字内容保持原样。 */
    private fun parseJsonArrayStrings(raw: String): List<String> = runCatching {
        when (val el = Json.parseToJsonElement(raw)) {
            is JsonArray -> el.map {
                when (it) {
                    is JsonPrimitive -> it.contentOrNull ?: ""
                    else -> it.toString()
                }
            }
            else -> listOf(raw)
        }
    }.getOrDefault(listOf(raw))

    private fun resolveVariable(name: String, scope: Map<String, String?>): String? =
        if (scope.containsKey(name)) scope[name] else name

    /** 官方 variables.js:635 performOperation。 */
    fun performOperation(
        value: String,
        operation: (List<Double>) -> Double,
        singleOperand: Boolean,
        scope: Map<String, String?> = emptyMap(),
    ): String {
        fun getResult(): Double {
            if (value.isEmpty()) return 0.0
            val array = parseNumericSeries(value, scope)
            if (array.isEmpty()) return 0.0
            val result = if (singleOperand) operation(listOf(array[0])) else operation(array)
            return if (result.isNaN()) 0.0 else result
        }
        return jsNumberToString(getResult())
    }

    // ---- 官方各运算回调 ----
    fun add(values: List<Double>) = values.fold(0.0) { a, b -> a + b }
    fun mul(values: List<Double>) = values.fold(1.0) { a, b -> a * b }
    fun minOfSeries(values: List<Double>) = values.min()
    fun maxOfSeries(values: List<Double>) = values.max()
    fun sub(values: List<Double>): Double =
        if (values.isEmpty()) 0.0 else values.drop(1).fold(values[0]) { a, b -> a - b }
    fun div(values: List<Double>): Double {
        val second = values.getOrNull(1) ?: Double.NaN
        return if (second == 0.0) 0.0 else values[0] / second
    }
    fun mod(values: List<Double>): Double {
        val second = values.getOrNull(1) ?: Double.NaN
        return if (second == 0.0) 0.0 else values[0] % second
    }
    fun pow(values: List<Double>): Double = values[0].pow(values.getOrElse(1) { Double.NaN })
    fun roundOp(values: List<Double>): Double = round(values[0])
    fun absOp(values: List<Double>): Double = abs(values[0])
    fun sqrtOp(values: List<Double>): Double = sqrt(values[0])
    fun sinOp(values: List<Double>): Double = java.lang.StrictMath.sin(values[0])
    fun cosOp(values: List<Double>): Double = java.lang.StrictMath.cos(values[0])
    fun logOp(values: List<Double>): Double = java.lang.StrictMath.log(values[0])

    /** 官方 variables.js:745 lenValuesCallback。 */
    fun lenValue(value: String): String {
        val parsed = parseJsonValue(value)
        return when (parsed) {
            is JsonArray -> parsed.size.toString()
            is JsonObject -> parsed.size.toString()
            // 注意：本版本 kotlinx 的 JsonNull 同时是 JsonPrimitive，必须排在 JsonPrimitive 之前
            JsonNull -> throw IllegalArgumentException("Cannot convert undefined or null to object")
            is JsonPrimitive -> when {
                parsed.isString -> parsed.content.length.toString()
                parsed.content.toDoubleOrNull() != null ->
                    jsNumberToString(parsed.content.toDouble()).length.toString()
                else -> "0" // boolean
            }
            null -> value.length.toString() // JSON.parse 失败 → 字符串长度
            else -> "0"
        }
    }

    /** 官方 variables.js:782-814 customSortComparitor + sortArrayObjectCallback。 */
    fun sortValue(value: String, keysort: String?): String {
        val parsed = parseJsonValue(value) ?: return value
        return when (parsed) {
            is JsonArray -> {
                val sorted = parsed.sortedWith { a, b -> jsCompare(a, b) }
                Json.encodeToString(JsonArray.serializer(), JsonArray(sorted))
            }
            is JsonObject -> {
                val keys = if (isFalseBoolean(keysort)) {
                    parsed.keys.sortedWith { a, b -> jsCompare(parsed.getValue(a), parsed.getValue(b)) }
                } else {
                    parsed.keys.sortedWith { a, b -> jsStringCompare(a, b) }
                }
                Json.encodeToString(JsonArray.serializer(), JsonArray(keys.map { JsonPrimitive(it) }))
            }
            JsonNull -> throw IllegalArgumentException("Cannot convert undefined or null to object")
            is JsonPrimitive -> Json.encodeToString(JsonPrimitive.serializer(), parsed)
            else -> value
        }
    }

    /** 官方 customSortComparitor：类型不同按 typeof 名比较，同类型按 JS > < 比较。 */
    fun jsCompare(a: JsonElement, b: JsonElement): Int {
        val ta = jsTypeOf(a)
        val tb = jsTypeOf(b)
        if (ta != tb) return jsStringCompare(ta, tb)
        return when {
            a is JsonPrimitive && a.isString && b is JsonPrimitive && b.isString ->
                jsStringCompare(a.content, b.content)
            a is JsonPrimitive && b is JsonPrimitive && a.content in setOf("true", "false") ->
                a.content.toBoolean().compareTo(b.content.toBoolean())
            a is JsonPrimitive && b is JsonPrimitive ->
                (a.content.toDoubleOrNull() ?: Double.NaN).compareTo(b.content.toDoubleOrNull() ?: Double.NaN)
            else -> 0
        }
    }

    private fun jsTypeOf(el: JsonElement): String = when (el) {
        is JsonPrimitive -> when {
            el.isString -> "string"
            el.content in setOf("true", "false") -> "boolean"
            else -> "number"
        }
        is JsonNull -> "object"
        else -> "object"
    }

    /** JS 字符串比较：UTF-16 码元字典序（Kotlin String.compareTo 同语义）。 */
    fun jsStringCompare(a: String, b: String): Int = a.compareTo(b)
}
