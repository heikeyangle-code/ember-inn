package com.emberinn.engine.prompt

import com.emberinn.engine.worldinfo.DepthEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** 官方行为差分：script.js generate 角色卡/群聊/世界书深度提示 setExtensionPrompt 规格。 */
class DepthPromptDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `depth prompt specs match official fixtures`() {
        val root = json.parseToJsonElement(
            checkNotNull(javaClass.getResource("/diff/depth-inject.json")).readText(),
        ).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val c = caseEl.jsonObject
            val id = c.getValue("id").jsonPrimitive.content
            val body = c.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = c.getValue("expected")
            when (body["mode"]?.jsonPrimitive?.content) {
                "character" -> {
                    val item = DepthPromptEngine.characterDepthPromptItem(
                        content = body["content"]?.jsonPrimitive?.content ?: "",
                        depth = body["depth"]?.jsonPrimitive?.intOrNull ?: DepthPromptEngine.DEFAULT_DEPTH,
                        role = body["role"]?.jsonPrimitive?.content ?: DepthPromptEngine.DEFAULT_ROLE,
                    )
                    val exp = expected.jsonObject
                    assertEquals("case $id key", exp["key"]!!.jsonPrimitive.content, item.identifier)
                    assertEquals("case $id value", exp["value"]!!.jsonPrimitive.content, item.content)
                    assertEquals("case $id depth", exp["depth"]!!.jsonPrimitive.intOrNull, item.injectionDepth)
                    assertEquals("case $id role", exp["role"]!!.jsonPrimitive.intOrNull, ExtensionPromptEngine.roleInt(item.role))
                    assertEquals("case $id order", 100, item.injectionOrder)
                }
                "group" -> {
                    val prompts = body["prompts"]!!.jsonArray.map { p ->
                        PromptItem(
                            identifier = "g",
                            name = "g",
                            content = p.jsonObject["text"]?.jsonPrimitive?.content ?: "",
                            role = p.jsonObject["role"]?.jsonPrimitive?.content ?: "system",
                            injectionDepth = p.jsonObject["depth"]?.jsonPrimitive?.intOrNull ?: 4,
                        )
                    }
                    val items = DepthPromptEngine.groupDepthPromptItems(prompts)
                    expected.jsonArray.forEachIndexed { index, expEl ->
                        val exp = expEl.jsonObject
                        val item = items[index]
                        assertEquals("case $id key $index", exp["key"]!!.jsonPrimitive.content, item.identifier)
                        assertEquals("case $id value $index", exp["value"]!!.jsonPrimitive.content, item.content)
                        assertEquals("case $id depth $index", exp["depth"]!!.jsonPrimitive.intOrNull, item.injectionDepth)
                        assertEquals("case $id role $index", exp["role"]!!.jsonPrimitive.intOrNull, ExtensionPromptEngine.roleInt(item.role))
                    }
                }
                "world" -> {
                    val entries = body["depthEntries"]!!.jsonArray.map { e ->
                        val o = e.jsonObject
                        DepthEntry(
                            depth = o["depth"]?.jsonPrimitive?.intOrNull ?: 4,
                            role = when (o["role"]?.jsonPrimitive?.intOrNull) {
                                1 -> "user"
                                2 -> "assistant"
                                else -> "system"
                            },
                            entries = o["entries"]!!.jsonArray.map { it.jsonPrimitive.content },
                        )
                    }
                    val items = DepthPromptEngine.worldInfoDepthPromptItems(entries)
                    expected.jsonArray.forEachIndexed { index, expEl ->
                        val exp = expEl.jsonObject
                        val item = items[index]
                        assertEquals("case $id key $index", exp["key"]!!.jsonPrimitive.content, item.identifier)
                        assertEquals("case $id value $index", exp["value"]!!.jsonPrimitive.content, item.content)
                        assertEquals("case $id depth $index", exp["depth"]!!.jsonPrimitive.intOrNull, item.injectionDepth)
                        assertEquals("case $id role $index", exp["role"]!!.jsonPrimitive.intOrNull, ExtensionPromptEngine.roleInt(item.role))
                    }
                }
            }
        }
    }
}
