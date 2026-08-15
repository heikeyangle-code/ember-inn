package com.emberinn.engine.provider

import com.emberinn.engine.provider.ModelSortEngine.ModelMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方 openai.js sortModelsBy / groupModelsByVendor 差分（48 例）。
 * fixture 由 scripts/diff/model-sort-official.mjs 生成（输入模型对象嵌入每例）。
 */
class ModelSortDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `model sort and group match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/model-sort.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray

        for (caseEl in cases) {
            val c = caseEl.jsonObject
            val kind = c.getValue("kind").jsonPrimitive.content
            val source = c.getValue("source").jsonPrimitive.content
            val expected = json.parseToJsonElement(c.getValue("expected").jsonPrimitive.content)
            val input = (c.getValue("input").jsonArray).map { modelFrom(it.jsonObject) }
            when (kind) {
                "sort" -> {
                    val property = c.getValue("property").jsonPrimitive.content
                    val out = ModelSortEngine.sortModelsBy(input, property, source)
                    assertEquals("sort($source,$property)", expected.jsonArray.map { it.jsonPrimitive.content }, out.map { it.id })
                }
                "group" -> {
                    val out = ModelSortEngine.groupModelsByVendor(input, source)
                    val expectedObj = expected.jsonObject
                    assertEquals("group($source) keys", expectedObj.keys.toList(), out.keys.toList())
                    for ((k, ids) in expectedObj) {
                        assertEquals("group($source).$k", ids.jsonArray.map { it.jsonPrimitive.content }, out[k]?.map { it.id })
                    }
                }
                else -> error("unknown kind: $kind")
            }
        }
    }

    private fun modelFrom(o: JsonObject): ModelMeta {
        val pricing = o["pricing"] as? JsonObject
        val info = o["info"] as? JsonObject
        return ModelMeta(
            id = o["id"]?.jsonPrimitive?.contentOrNull ?: "",
            name = o["name"]?.jsonPrimitive?.contentOrNull,
            contextLength = o["context_length"]?.jsonPrimitive?.longOrNull,
            pricingPrompt = pricing?.get("prompt")?.jsonPrimitive?.doubleOrNull,
            pricingCompletion = pricing?.get("completion")?.jsonPrimitive?.doubleOrNull,
            pricingInput = pricing?.get("input")?.jsonPrimitive?.doubleOrNull,
            pricingOutput = pricing?.get("output")?.jsonPrimitive?.doubleOrNull,
            tokens = o["tokens"]?.jsonPrimitive?.longOrNull,
            infoName = info?.get("name")?.jsonPrimitive?.contentOrNull,
            infoContextLength = info?.get("contextLength")?.jsonPrimitive?.longOrNull,
            infoDeveloper = info?.get("developer")?.jsonPrimitive?.contentOrNull,
            endpoints = (o["endpoints"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull },
            type = o["type"]?.jsonPrimitive?.contentOrNull,
        )
    }
}
