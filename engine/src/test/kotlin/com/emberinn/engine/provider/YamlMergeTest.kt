package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方 util.js mergeObjectWithYaml / excludeKeysByYaml 的 SnakeYAML 移植单测。
 * SnakeYAML 2.6 行为已实测对齐 js-yaml：锚点/别名/合并键（<<）原生解析；
 * 多文档抛异常 → 官方 try/catch 静默忽略（不合并）。
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
    fun `nested maps and lists merge`() {
        val body = buildJsonObject { put("model", JsonPrimitive("m")) }
        val out = YamlMerge.merge(body, "provider:\n  order:\n    - a\n    - b\n  allow: true\n")
        val provider = out["provider"] as? JsonObject
        assertEquals(true, provider?.get("allow")?.toString()?.toBoolean())
        assertEquals(2, (provider?.get("order") as? JsonArray)?.size)
        assertEquals("a", (provider?.get("order") as? JsonArray)?.get(0)?.toString()?.trim('"'))
    }

    @Test
    fun `headers parse flat yaml`() {
        val h = YamlMerge.headers("X-Api-Key: secret\nAccept: application/json")
        assertEquals("secret", h["X-Api-Key"])
        assertEquals("application/json", h["Accept"])
    }

    @Test
    fun `anchors aliases and merge keys resolve like js-yaml`() {
        val body = buildJsonObject { put("model", JsonPrimitive("m")) }
        val out = YamlMerge.merge(
            body,
            "base: &b\n  x: 1\n  y: 2\nchild:\n  <<: *b\n  y: 3\n",
        )
        val child = out["child"] as? JsonObject
        // 官方 'yaml' 包不合并 <<：child 保留字面键 "<<": {x:1,y:2}，y 覆盖为 3
        val merged = child?.get("<<") as? JsonObject
        assertEquals("1", merged?.get("x")?.toString())
        assertEquals("2", merged?.get("y")?.toString())
        assertEquals("3", child?.get("y")?.toString())
        // 顶层对象整体 Object.assign：base/child 都是顶层键，全部进 body
        assertEquals("1", (out["base"] as? JsonObject)?.get("x")?.toString())
        assertEquals("2", (out["base"] as? JsonObject)?.get("y")?.toString())
    }

    @Test
    fun `multi document yaml is ignored like official try catch`() {
        val body = buildJsonObject { put("model", JsonPrimitive("m")) }
        val out = YamlMerge.merge(body, "a: 1\n---\nb: 2\n")
        assertEquals(listOf("model"), out.keys.toList())
    }

    @Test
    fun `top level array merges each object item sequentially`() {
        val body = buildJsonObject { put("model", JsonPrimitive("m")) }
        val out = YamlMerge.merge(body, "- {a: 1}\n- {b: 2, a: 9}\n")
        assertEquals("9", out["a"]?.toString())
        assertEquals("2", out["b"]?.toString())
    }

    @Test
    fun `inline list and quoted scalars`() {
        val out = YamlMerge.parse("stop: [x, y]\nname: \"a: b\"") as? JsonObject
        assertEquals(2, (out?.get("stop") as? JsonArray)?.size)
        assertEquals("a: b", (out?.get("name") as? JsonPrimitive)?.content)
    }

}
