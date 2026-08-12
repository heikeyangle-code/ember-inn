package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：PromptManager.js 纯逻辑（Prompt 构造默认值 / shouldTrigger / preparePrompt /
 * getPromptCollection / getPromptOrderForCharacter）。
 * fixture 由 scripts/diff/prompt-manager-official.mjs 生成；官方 new Prompt() 不复制
 * enabled/marker，比较只取官方输出的字段子集（脚本头部登记）。
 */
class PromptManagerDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `prompt manager outputs match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/prompt-manager.json"))
        val cases = json.parseToJsonElement(resource.readText()).jsonArray

        for (caseEl in cases) {
            val c = caseEl.jsonObject
            val kind = c.getValue("kind").jsonPrimitive.content
            val expected = json.parseToJsonElement(c.getValue("expected").jsonPrimitive.content)
            when (kind) {
                "constructor" -> {
                    val args = c.getValue("args").jsonObject
                    assertSubset(itemFrom(args), expected.jsonObject, "constructor")
                }
                "trigger" -> {
                    val prompt = (c["prompt"] as? JsonObject)?.let { itemFrom(it) }
                    val type = c.getValue("type").jsonPrimitive.content
                    assertEquals("trigger", expected.jsonPrimitive.content, PromptManagerCore.shouldTrigger(prompt, type).toString())
                }
                "prepare" -> {
                    val item = PromptItem(
                        identifier = "id1",
                        name = "N",
                        content = c.getValue("content").jsonPrimitive.content,
                        role = "system",
                    )
                    val original = c["original"]?.let { (it as JsonPrimitive).contentOrNull }
                    val prepared = PromptManagerCore.prepare(item, MacroEnv(user = "User", char = "Char"), original = original)
                    assertSubset(prepared, expected.jsonObject, "prepare")
                }
                "collection" -> {
                    val order = c.getValue("order").jsonArray.map { orderEntryFrom(it.jsonObject) }
                    val prompts = listOf(
                        PromptItem("main", "Main", content = "M {{user}}", role = "system", systemPrompt = true),
                        PromptItem("worldInfoBefore", "WI", content = "WI", role = "system"),
                        PromptItem("chatHistory", "History", content = "", role = "system", marker = true),
                        PromptItem("jailbreak", "JB", content = "JB", role = "system"),
                        PromptItem("custom", "Custom", content = "C {{original}}", role = "user", systemPrompt = false, injectionTrigger = listOf("normal")),
                        PromptItem("triggered", "T", content = "T", role = "system", injectionTrigger = listOf("continue")),
                    )
                    val collection = PromptManagerCore.getCollection(
                        order, prompts, c.getValue("type").jsonPrimitive.content,
                        MacroEnv(user = "User", char = "Char"),
                    )
                    val expectedArr = expected.jsonArray
                    assertEquals("collection size", expectedArr.size, collection.collection.size)
                    for (i in expectedArr.indices) {
                        assertSubset(collection.collection[i], expectedArr[i].jsonObject, "collection[$i]")
                    }
                }
                "order" -> {
                    val character = (c["character"] as? JsonObject)?.get("id")?.let { (it as JsonPrimitive).contentOrNull }
                    val lists = (c["list"]?.jsonArray ?: JsonArray(emptyList())).map { el ->
                        val o = el.jsonObject
                        PromptOrderList(
                            characterId = o["character_id"]?.let { (it as JsonPrimitive).contentOrNull },
                            order = (o["order"]?.jsonArray ?: JsonArray(emptyList())).map { orderEntryFrom(it.jsonObject) },
                        )
                    }
                    val resolved = PromptManagerCore.resolveOrder(character, lists)
                    val expectedArr = expected.jsonArray
                    assertEquals("order size", expectedArr.size, resolved.size)
                    for (i in expectedArr.indices) {
                        val e = expectedArr[i].jsonObject
                        val r = resolved[i]
                        assertEquals("order[$i].identifier", e.getValue("identifier").jsonPrimitive.content, r.identifier)
                        assertEquals("order[$i].enabled", e["enabled"]?.jsonPrimitive?.content ?: "true", r.enabled.toString())
                    }
                }
                else -> error("unknown kind: $kind")
            }
        }
    }

    private fun itemFrom(o: JsonObject): PromptItem = PromptItem(
        identifier = o["identifier"]?.let { (it as JsonPrimitive).contentOrNull } ?: "",
        name = o["name"]?.let { (it as JsonPrimitive).contentOrNull } ?: "",
        content = o["content"]?.let { (it as JsonPrimitive).contentOrNull } ?: "",
        role = o["role"]?.let { (it as JsonPrimitive).contentOrNull } ?: "system",
        systemPrompt = o["system_prompt"]?.let { (it as JsonPrimitive).contentOrNull } != "false",
        marker = o["marker"]?.let { (it as JsonPrimitive).contentOrNull } == "true",
        position = o["position"]?.let { (it as JsonPrimitive).contentOrNull },
        injectionPosition = o["injection_position"]?.let { (it as JsonPrimitive).contentOrNull }?.toIntOrNull(),
        injectionDepth = o["injection_depth"]?.let { (it as JsonPrimitive).contentOrNull }?.toIntOrNull(),
        injectionOrder = o["injection_order"]?.let { (it as JsonPrimitive).contentOrNull }?.toIntOrNull() ?: 100,
        injectionTrigger = ((o["injection_trigger"] as? JsonArray) ?: JsonArray(emptyList())).map { it.jsonPrimitive.content },
        forbidOverrides = o["forbid_overrides"]?.let { (it as JsonPrimitive).contentOrNull } == "true",
        extension = o["extension"]?.let { (it as JsonPrimitive).contentOrNull } == "true",
    )

    private fun orderEntryFrom(o: JsonObject): PromptOrderEntry = PromptOrderEntry(
        identifier = o["identifier"]?.let { (it as JsonPrimitive).contentOrNull } ?: "",
        enabled = o["enabled"]?.let { (it as JsonPrimitive).contentOrNull } != "false",
        injectionPosition = o["injection_position"]?.let { (it as JsonPrimitive).contentOrNull }?.toIntOrNull(),
        injectionDepth = o["injection_depth"]?.let { (it as JsonPrimitive).contentOrNull }?.toIntOrNull(),
        injectionOrder = o["injection_order"]?.let { (it as JsonPrimitive).contentOrNull }?.toIntOrNull(),
        role = o["role"]?.let { (it as JsonPrimitive).contentOrNull },
    )

    /** 官方输出只含定义过的字段：逐键比对，引擎缺省字段（如 systemPrompt=true）不参与。 */
    private fun assertSubset(item: PromptItem, expected: JsonObject, label: String) {
        for ((key, expectedValue) in expected) {
            val ev = expectedValue
            val actual = when (key) {
                "identifier" -> item.identifier
                "role" -> item.role
                "content" -> item.content
                "name" -> item.name
                "system_prompt" -> item.systemPrompt.toString()
                "position" -> item.position.orEmpty()
                "injection_depth" -> item.injectionDepth?.toString().orEmpty()
                "injection_position" -> item.injectionPosition?.toString().orEmpty()
                "forbid_overrides" -> item.forbidOverrides.toString()
                "extension" -> item.extension.toString()
                "injection_order" -> item.injectionOrder?.toString().orEmpty()
                "injection_trigger" -> item.injectionTrigger.joinToString(",")
                else -> continue
            }
            if (ev is JsonArray) {
                assertEquals("$label.$key", item.injectionTrigger.joinToString(","), actual)
            } else if (ev is JsonPrimitive && ev.isString) {
                assertEquals("$label.$key", ev.content, actual)
            } else if (ev is JsonPrimitive) {
                assertEquals("$label.$key", ev.content, actual)
            }
        }
    }
}
