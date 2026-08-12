package com.emberinn.engine.slash

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方行为差分测试：fixture 由 scripts/diff/slash-math-official.mjs 生成，
 * 覆盖 variables.js 数学运算 / evalBoolean / len / sort 全部可枚举分支。
 */
class SlashMathDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `slash math outputs match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/slash-math.json"))
        val cases = json.parseToJsonElement(resource.readText()).jsonArray
        assertTrue(cases.isNotEmpty())

        for (caseEl in cases) {
            val c = caseEl.jsonObject
            val kind = c.getValue("kind").jsonPrimitive.content
            val expected = c.getValue("expected").jsonPrimitive.content
            val throws = c.getValue("throws").jsonPrimitive.content == "true"

            val actual = try {
                when (kind) {
                    "math" -> runMath(c)
                    "bool" -> runBool(c)
                    "len" -> SlashMathEngine.lenValue(c.getValue("value").jsonPrimitive.content)
                    "sort" -> SlashMathEngine.sortValue(
                        c.getValue("value").jsonPrimitive.content,
                        c["keysort"]?.jsonPrimitive?.contentOrNull,
                    )
                    else -> error("unknown kind: $kind")
                }
            } catch (e: Exception) {
                if (throws) e.message.orEmpty() else throw AssertionError("unexpected throw: $e", e)
            }
            assertEquals("case $kind: ${(c["op"] as? JsonPrimitive)?.contentOrNull ?: (c["value"] as? JsonPrimitive)?.contentOrNull}", expected, actual)
        }
    }

    private fun runMath(c: JsonObject): String {
        val value = c.getValue("value").jsonPrimitive.content
        val scope = (c["scope"] as? JsonObject)?.entries?.associate {
            it.key to it.value.jsonPrimitive.contentOrNull
        } ?: emptyMap()
        val op = c.getValue("op").jsonPrimitive.content
        return when (op) {
            "add" -> SlashMathEngine.performOperation(value, SlashMathEngine::add, false, scope)
            "mul" -> SlashMathEngine.performOperation(value, SlashMathEngine::mul, false, scope)
            "min" -> SlashMathEngine.performOperation(value, SlashMathEngine::minOfSeries, false, scope)
            "max" -> SlashMathEngine.performOperation(value, SlashMathEngine::maxOfSeries, false, scope)
            "sub" -> SlashMathEngine.performOperation(value, SlashMathEngine::sub, false, scope)
            "div" -> SlashMathEngine.performOperation(value, SlashMathEngine::div, false, scope)
            "mod" -> SlashMathEngine.performOperation(value, SlashMathEngine::mod, false, scope)
            "pow" -> SlashMathEngine.performOperation(value, SlashMathEngine::pow, false, scope)
            "round" -> SlashMathEngine.performOperation(value, SlashMathEngine::roundOp, true, scope)
            "abs" -> SlashMathEngine.performOperation(value, SlashMathEngine::absOp, true, scope)
            "sqrt" -> SlashMathEngine.performOperation(value, SlashMathEngine::sqrtOp, true, scope)
            "sin" -> SlashMathEngine.performOperation(value, SlashMathEngine::sinOp, true, scope)
            "cos" -> SlashMathEngine.performOperation(value, SlashMathEngine::cosOp, true, scope)
            "log" -> SlashMathEngine.performOperation(value, SlashMathEngine::logOp, true, scope)
            else -> error("unknown op: $op")
        }
    }

    private fun runBool(c: JsonObject): String {
        val rule = c["rule"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "__none__" }
        val a = operand(c["a"])
        val b = operand(c["b"])
        return SlashMathEngine.evalBoolean(rule, a, b).toString()
    }

    private fun operand(el: kotlinx.serialization.json.JsonElement?): Any? {
        if (el == null) return null
        val p = el.jsonPrimitive
        return if (p.isString) {
            val s = p.content
            if (s == "__none__") null else s
        } else {
            p.content.toDoubleOrNull()
        }
    }
}
