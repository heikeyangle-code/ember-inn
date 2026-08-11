package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：extensions/memory 纯逻辑。
 * fixture 由 scripts/diff/memory-official.mjs 生成，禁止手改。
 */
class MemoryDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `memory matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/memory.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")
            val chat = body["chat"]?.jsonArray.orEmpty().map { it.jsonObject.toMemoryMessage() }

            when (body.getValue("method").jsonPrimitive.content) {
                "latest" -> assertEquals(
                    "case $id",
                    expected.jsonPrimitive.content,
                    MemoryEngine.getLatestMemoryFromChat(chat),
                )
                "index" -> assertEquals(
                    "case $id",
                    expected.jsonPrimitive.content.toInt(),
                    MemoryEngine.getIndexOfLatestChatSummary(chat),
                )
                "prompt" -> assertEquals(
                    "case $id",
                    expected.jsonPrimitive.content,
                    MemoryEngine.getSummaryPromptForNow(
                        chat = chat,
                        promptInterval = body.int("promptInterval", 10),
                        promptForceWords = body.int("promptForceWords"),
                        promptWords = body.int("promptWords", 200),
                        force = body.bool("force"),
                        prompt = body["prompt"]?.jsonPrimitive?.content ?: "Summarize.",
                    ),
                )
                "raw" -> {
                    val result = MemoryEngine.getRawSummaryPrompt(
                        chat = chat,
                        prompt = body["prompt"]?.jsonPrimitive?.content ?: "Summarize.",
                        maxMessagesPerRequest = body.int("maxMessagesPerRequest"),
                        promptSize = body.int("promptSize", 4096),
                    )
                    val e = expected.jsonObject
                    assertEquals("case $id rawPrompt", e["rawPrompt"]?.jsonPrimitive?.content ?: "", result.rawPrompt)
                    assertEquals("case $id lastUsedIndex", e["lastUsedIndex"]?.jsonPrimitive?.content?.toInt() ?: -1, result.lastUsedIndex)
                }
                "format" -> {
                    val value = body["value"]?.jsonPrimitive?.content ?: ""
                    val template = body["template"]?.jsonPrimitive?.contentOrNull ?: MemoryEngine.DEFAULT_TEMPLATE
                    val actual = MemoryEngine.formatMemoryValue(value, template) {
                        it.replace("{{summary}}", value.trim()).replace("{{user}}", "User")
                    }
                    assertEquals("case $id", expected.jsonPrimitive.content, actual)
                }
            }
        }
    }

    private fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
        this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: default

    private fun JsonObject.int(key: String, default: Int = 0): Int =
        this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: default

    private fun JsonObject.toMemoryMessage(): MemoryMessage = MemoryMessage(
        name = this["name"]?.jsonPrimitive?.content ?: "User",
        mes = this["mes"]?.jsonPrimitive?.content ?: "",
        isSystem = this["is_system"]?.jsonPrimitive?.content == "true",
        memory = this["extra"]?.jsonObject?.get("memory")?.jsonPrimitive?.content,
    )
}
