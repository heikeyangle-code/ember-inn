package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方 util.js mergeObjectWithYaml / excludeKeysByYaml 的标量子集单测。
 * 完整 yaml 库（嵌套对象/列表/多文档）登记边界；harness 无法对拍（node_modules/yaml 不在参考仓库）。
 */
class YamlMergeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `merge flat scalars into body`() {
        val body = buildJsonObject { put("model", JsonPrimitive("m")) }
        val out = YamlMerge.merge(body, "temperature: 0.7\nstream: true\nstop: [x]\nname: \"a: b\"")
        assertEquals("0.7", out["temperature"]?.toString())
        assertEquals("true", out["stream"]?.toString())
        assertEquals("a: b", (out["name"] as? JsonPrimitive)?.content)
        assertEquals("m", (out["model"] as? JsonPrimitive)?.content)
    }

    @Test
    fun `exclude removes keys from array and object forms`() {
        val body = buildJsonObject {
            put("a", JsonPrimitive(1))
            put("b", JsonPrimitive(2))
            put("c", JsonPrimitive(3))
        }
        val out = YamlMerge.excludeKeys(body, "- a\n- c")
        assertEquals(listOf("b"), out.keys.toList())
        val out2 = YamlMerge.excludeKeys(body, "b: whatever")
        assertEquals(listOf("a", "c"), out2.keys.toList())
    }

    @Test
    fun `headers parse flat yaml`() {
        val h = YamlMerge.headers("X-Api-Key: secret\nAccept: application/json")
        assertEquals("secret", h["X-Api-Key"])
        assertEquals("application/json", h["Accept"])
    }
}
