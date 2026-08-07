package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.worldinfo.TokenCounter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：populateDialogueExamples（openai.js）。
 * fixture 由 scripts/diff/dialogue-examples-pop-official.mjs 生成，禁止手改。
 */
class DialogueExamplesPopDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `dialogue examples population matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/dialogue-examples-pop.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val dialogues = body["messageExamples"]?.jsonArray.orEmpty().map { d ->
                d.jsonArray.map { m ->
                    val obj = m.jsonObject
                    ExampleMessage(
                        name = obj["name"]?.jsonPrimitive?.let { if (it.isString) it.content else null },
                        content = obj["content"]?.jsonPrimitive?.content ?: "",
                    )
                }
            }

            val chatCompletion = ChatCompletion(TokenHandler(TokenCounter { it.length }))
            chatCompletion.setTokenBudget(body["budget"]?.jsonPrimitive?.content?.toIntOrNull() ?: 100000, 0)
            val prompts = PromptItems()
            prompts.add(PromptItem(identifier = "dialogueExamples", name = "dialogueExamples", role = "system", content = ""))

            DialogueExamplesPopulator.populate(
                chatCompletion = chatCompletion,
                handler = TokenHandler(TokenCounter { it.length }),
                prompts = prompts,
                dialogues = dialogues,
                newExampleChatPrompt = body["new_example_chat_prompt"]?.jsonPrimitive?.content ?: "",
                env = MacroEnv(user = "", char = ""),
            )

            val actual = JsonArray(
                chatCompletion.entries.mapNotNull { it as? ChatEntry.Collection }.map { col ->
                    buildJsonObject {
                        put("name", JsonPrimitive(col.collection.identifier))
                        put("msgs", JsonArray(col.collection.items.map { m ->
                            buildJsonObject {
                                put("role", JsonPrimitive(m.role))
                                put("content", JsonPrimitive(m.content))
                                put("identifier", JsonPrimitive(m.identifier ?: ""))
                                put("name", m.name?.let { JsonPrimitive(it) } ?: JsonNull)
                            }
                        }))
                    }
                },
            )
            assertEquals("case $id", expected, actual)
        }
    }
}
