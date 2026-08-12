package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptManagerCoreTest {

    private val env = MacroEnv(user = "User", char = "Char")
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `default order matches official when injected by caller`() {
        // 官方 getPromptOrderForCharacter 无存储时返回 []；默认顺序由调用方（PromptAssembler）注入
        val ids = PromptManagerCore.getCollection(PromptManagerCore.DEFAULT_ORDER_ENTRIES, emptyList(), "normal", env)
            .collection.map { it.identifier }
        assertEquals(
            listOf(
                "main", "worldInfoBefore", "personaDescription", "charDescription",
                "charPersonality", "scenario", "nsfw", "worldInfoAfter",
                "dialogueExamples", "chatHistory", "jailbreak",
            ),
            ids,
        )
    }

    @Test
    fun `user order overrides default and disabled entries excluded`() {
        val order = listOf(
            PromptOrderEntry("jailbreak"),
            PromptOrderEntry("main", enabled = false),
            PromptOrderEntry("nsfw"),
        )
        val ids = PromptManagerCore.getCollection(order, emptyList(), "normal", env)
            .collection.map { it.identifier }
        assertEquals(listOf("jailbreak", "main", "nsfw"), ids)
        // main 被禁用时仍保留空占位（官方相对插入依赖）
        assertEquals("", PromptManagerCore.getCollection(order, emptyList(), "normal", env)
            .collection.first { it.identifier == "main" }.content)
    }

    @Test
    fun `injection trigger filters by generation type`() {
        val userPrompt = PromptItem(
            identifier = "main",
            name = "Main Prompt",
            content = "只能冒充",
            injectionTrigger = listOf("impersonate"),
        )
        val normal = PromptManagerCore.getCollection(
            listOf(PromptOrderEntry("main")), listOf(userPrompt), "normal", env,
        )
        // 官方：main 触发条件不满足时仍保留空占位（相对插入依赖）
        assertTrue(normal.has("main"))
        assertEquals("", normal.get("main")?.content)

        val impersonate = PromptManagerCore.getCollection(
            listOf(PromptOrderEntry("main")), listOf(userPrompt), "impersonate", env,
        )
        assertTrue(impersonate.has("main"))
        assertEquals("只能冒充", impersonate.get("main")?.content)
    }

    @Test
    fun `prepare substitutes original and names`() {
        val prompt = PromptItem("main", "Main", content = "{{original}} {{user}}/{{char}}")
        val out = PromptManagerCore.prepare(prompt, env, original = "X")
        assertEquals("X User/Char", out.content)
    }

    @Test
    fun `merge applies role and injection overrides`() {
        val items = PromptItems(
            listOf(
                PromptItem(
                    identifier = "charDescription",
                    name = "Char Description",
                    marker = true,
                    role = "user",
                    injectionPosition = PromptInjection.ABSOLUTE,
                    injectionDepth = 6,
                    injectionOrder = 7,
                ),
            ),
        )
        val merged = PromptManagerCore.mergeSystemPrompts(
            items,
            listOf(PromptMessage("system", "描述内容", identifier = "charDescription")),
        )
        val out = merged.collection.single()
        assertEquals("描述内容", out.content)
        assertEquals("user", out.role)
        assertEquals(PromptInjection.ABSOLUTE, out.injectionPosition)
        assertEquals(6, out.injectionDepth)
        assertEquals(7, out.injectionOrder)
    }

    @Test
    fun `order list serializes to official snake case`() {
        val list = PromptOrderList(
            characterId = "c1",
            order = listOf(
                PromptOrderEntry("main", enabled = false),
                PromptOrderEntry("nsfw", injectionPosition = 1, injectionDepth = 4, injectionOrder = 100),
            ),
        )
        val text = json.encodeToString(PromptOrderList.serializer(), list)
        val decoded = json.decodeFromString(PromptOrderList.serializer(), text)
        assertEquals(list, decoded)
        assertTrue(text.contains("\"character_id\""))
        assertTrue(text.contains("\"injection_depth\""))
    }
}
